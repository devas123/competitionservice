package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.logError
import org.apache.kafka.clients.consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.kafka.consumer._
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.{Chunk, RIO, Ref, ZIO}

import scala.util.{Failure, Success, Try}

private[kafka] object KafkaQueryAndSubscribeActor {

  def apply(name: String, actorRefProvider: ActorRefProvider)(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    brokers: List[String],
    subscribe: Boolean,
    query: Boolean,
    startOffset: Long = 0L
  ): ZIO[Logging with Clock with Blocking with Console, Throwable, ActorRef[KafkaQueryActorCommand]] = for {
    initState <- Ref.make(false)
    actor <- actorRefProvider.make(name, ActorConfig(), initState, behavior(topic, groupId, replyTo, brokers, subscribe, query, startOffset))
  } yield actor

  sealed trait KafkaQueryActorCommand

  case object Stop extends KafkaQueryActorCommand

  case class ForwardMesage(msg: MessageReceived, to: ActorRef[KafkaConsumerApi]) extends KafkaQueryActorCommand

  import Behaviors._

  private[kafka] def behavior(
                               topic: String,
                               groupId: String,
                               replyTo: ActorRef[KafkaConsumerApi],
                               brokers: List[String],
                               subscribe: Boolean,
                               query: Boolean,
                               startOffset: Long = 0L
                             ): ActorBehavior[Logging with Clock with Blocking, Ref[Boolean], KafkaQueryActorCommand] = {

    def queryAndSendEvents(): ZIO[Clock with Blocking with Logging, Throwable, Unit] = {
      for {
        _ <- replyTo ! QueryStarted()
        result <- retrieveEvents(topic, startOffset).fold(e => QueryError(e), _ => QueryFinished())
        _ <- Logging.info(s"Done collecting events: $result")
        _ <- replyTo ! result
      } yield ()
    }

    def startByteArrayStream(
                              topic: String,
                              action: CommittableRecord[String, Try[Array[Byte]]] => ZIO[Logging, Throwable, Unit],
                              stopSignal: Ref[Boolean]
                            ): ZIO[Logging with Clock with Blocking, Throwable, Unit] = {
      Consumer.subscribeAnd(Subscription.topics(topic)).plainStream(Serde.string, Serde.byteArray.asTry)
        .mapM(record => action(record).as(record)).map(_.offset)
        .aggregateAsync(Consumer.offsetBatches)
        .mapM(_.commit)
        .takeUntilM(_ => stopSignal.get)
        .runDrain
    }.provideSomeLayer[Logging with Clock with Blocking](
      Consumer.make(ConsumerSettings(brokers).withGroupId(groupId).withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(
        Consumer.AutoOffsetStrategy.Earliest
      ))).toLayer
    )

    def retrieveEvents(topic: String, startOffset: Long): RIO[Clock with Blocking with Logging, Unit] = {
      for {
        partitions      <- Consumer.partitionsFor(topic)
        endOffsets      <- Consumer.endOffsets(partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet)
        offset          <- Consumer.committed(endOffsets.keySet)
        filteredOffsets <- RIO(endOffsets.filter(_._2 > 0))
        _ <- Logging.info(s"Getting events from topic $topic, endOffsets: $endOffsets, start from $offset")
        res <- (for {
          _ <- Logging.info(s"Filtered offsets: $filteredOffsets")
          off = filteredOffsets.keySet.map(tp => {
            val partition       = tp
            val committedOffset = offset.get(partition).flatten.getOrElse(new consumer.OffsetAndMetadata(0)).offset()
            ((tp.topic(), tp.partition()), endOffsets(partition) - committedOffset)
          }).filter(o => o._2 > 0)
          numberOfEventsToTake = off.foldLeft(0L)((acc, el) => acc + el._2) - startOffset
          _ <- Logging.info(s"Effective offsets to retrieve: $off, number of events: $numberOfEventsToTake")
          _ <-
            if (numberOfEventsToTake > 0) {
              Consumer.subscribeAnd(Subscription.manual(off.map(_._1).toIndexedSeq: _*))
                .plainStream(Serde.string, Serde.byteArray)
                .take(numberOfEventsToTake)
                .mapM(e => (replyTo ! MessageReceived(topic = topic, committableRecord = e)).as(e))
                .map(_.offset)
                .aggregateAsync(Consumer.offsetBatches)
                .mapM(_.commit).runDrain
            } else {
              RIO.effect(Chunk.empty)
            }
        } yield numberOfEventsToTake).when(filteredOffsets.nonEmpty)
      } yield res
    }.onError(err => CompetitionLogging.logError(err.squashTrace)).provideSomeLayer[Clock with Blocking with Logging](
      Consumer.make(ConsumerSettings(brokers).withGroupId(groupId).withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(
        Consumer.AutoOffsetStrategy.Earliest
      ))).toLayer
    )

    Behaviors.behavior[Logging with Clock with Blocking, Ref[Boolean], KafkaQueryActorCommand].withReceive {
      (context, _, state, command, _) =>
        command match {
          case ForwardMesage(msg, to) => Logging.info(s"Forwarding message to actor: $to") *> (to ! msg).as(state)
          case Stop => context.stopSelf.as(state)
        }
    }.withPostStop { (_, context, state, _) => Logging.info(s"Stopping ${context.self}.") *> state.set(true) }.withInit { (_, context, state, _) => {
      for {
        _ <- context.watchWith(Stop, replyTo)
        executeQuery <- queryAndSendEvents().when(query).fork
        _ <- executeQuery.join
        fiber <- (for {
          _ <- startByteArrayStream(
            topic,
            { record =>
              val tryValue: Try[Array[Byte]] = record.record.value()
              tryValue match {
                case Failure(exception) => for {
                  _ <- Logging.error("Error during deserialization")
                  _ <- logError(exception)
                } yield ()
                case Success(value) =>
                  val newConsumerRecord = new ConsumerRecord[String, Array[Byte]](
                    topic,
                    record.partition,
                    record.offset.offset,
                    record.key,
                    value
                  )
                  context.self !
                    ForwardMesage(MessageReceived(topic, record.copy(record = newConsumerRecord)), replyTo)
              }
            },
            state
          ).when(subscribe)
          _ <- Logging.info("Finished consuming from kafka. Stopping.")
          _ <- context.self ! Stop
        } yield ()).onExit(exit => Logging.info(s"Kafka query/subscription actor ${context.self} fiber exit $exit.")).fork
      } yield (Seq(fiber), Seq.empty, state)
    }
    }
  }
}
