package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.logError
import org.apache.kafka.clients.consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import zio.{Chunk, Fiber, RIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer._
import zio.kafka.serde.Serde
import zio.logging.Logging

import scala.util.{Failure, Success, Try}

private[kafka] object KafkaQueryAndSubscribeActor {

  sealed trait KafkaQueryActorCommand[+_]
  case object Stop                                                               extends KafkaQueryActorCommand[Unit]
  case class ForwardMesage(msg: MessageReceived, to: ActorRef[KafkaConsumerApi]) extends KafkaQueryActorCommand[Unit]

  def behavior(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    brokers: List[String],
    subscribe: Boolean,
    query: Boolean,
    startOffset: Long = 0L
  ): ActorBehavior[Logging with Clock with Blocking, Unit, KafkaQueryActorCommand] =
    new ActorBehavior[Logging with Clock with Blocking, Unit, KafkaQueryActorCommand] {
      override def receive[A](
        context: Context[KafkaQueryActorCommand],
        actorConfig: ActorConfig,
        state: Unit,
        command: KafkaQueryActorCommand[A],
        timers: Timers[Logging with Clock with Blocking, KafkaQueryActorCommand]
      ): RIO[Logging with Clock with Blocking, (Unit, A)] = command match {
        case ForwardMesage(msg, to) => (to ! msg).as(((), ().asInstanceOf[A]))
        case Stop                   => context.stopSelf.as(((), ().asInstanceOf[A]))
      }
      override def init(
        actorConfig: ActorConfig,
        context: Context[KafkaQueryActorCommand],
        initState: Unit,
        timers: Timers[Logging with Clock with Blocking, KafkaQueryActorCommand]
      ): RIO[
        Logging with Clock with Blocking,
        (Seq[Fiber[Throwable, Unit]], Seq[KafkaQueryActorCommand[Any]], Unit)
      ] = {
        for {
          _ <- super.init(actorConfig, context, initState, timers)
          _ <-
            if (query) { queryAndSendEvents() }
            else { ZIO.unit }
          fiber <- (for {
            _ <-
              if (subscribe) {
                getByteArrayStream(
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
                        Logging.info(s"Forwarding message to actor: $replyTo") *>
                          (context.self !
                            ForwardMesage(MessageReceived(topic, record.copy(record = newConsumerRecord)), replyTo))
                    }
                  }
                )
              } else { ZIO.unit }
            _ <- Logging.info("Stopping the subscription.")
            _ <- context.self ! Stop
          } yield ()).onError(err => Logging.error(err.prettyPrint)).fork
        } yield (Seq(fiber), Seq.empty, ())
      }

      def queryAndSendEvents(): ZIO[Clock with Blocking with Logging, Throwable, Unit] = {
        for {
          _      <- replyTo ! QueryStarted()
          result <- retrieveEvents(topic, startOffset).fold(e => QueryError(e), _ => QueryFinished())
          _      <- replyTo ! result
        } yield ()
      }

      def getByteArrayStream(
        topic: String,
        action: CommittableRecord[String, Try[Array[Byte]]] => ZIO[Logging, Throwable, Unit]
      ): ZIO[Logging with Clock with Blocking, Throwable, Unit] = {
        Consumer.subscribeAnd(Subscription.topics(topic)).plainStream(Serde.string, Serde.byteArray.asTry)
          .mapM(record => action(record).as(record)).map(_.offset).aggregateAsync(Consumer.offsetBatches).mapM(_.commit)
          .runDrain
      }.provideSomeLayer[Logging with Clock with Blocking](
        Consumer.make(ConsumerSettings(brokers).withGroupId(groupId).withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(
          Consumer.AutoOffsetStrategy.Earliest
        ))).toLayer
      )

      def retrieveEvents(
        topic: String,
        startOffset: Long
      ): RIO[Clock with Blocking with Logging, Unit] = {
        for {
          partitions <- Consumer.partitionsFor(topic)
          endOffsets <- Consumer.endOffsets(partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet)
          offset     <- Consumer.committed(endOffsets.keySet)
          filteredOffsets <- RIO(endOffsets.filter(_._2 > 0))
          _ <- Logging.info(s"Getting events from topic $topic, endOffsets: $endOffsets, start from $offset")
          res <-
            if (filteredOffsets.nonEmpty) {
              for {
                _ <- Logging.info(s"Filtered offsets: $filteredOffsets")
                off = filteredOffsets.keySet.map(tp => {
                  val partition = tp
                  val committedOffset = offset.get(partition).flatten.getOrElse(new consumer.OffsetAndMetadata(0))
                    .offset()
                  ((tp.topic(), tp.partition()), endOffsets(partition) - committedOffset)
                }).filter(o => o._2 > 0)
                numberOfEventsToTake = off.foldLeft(0L)((acc, el) => acc + el._2) - startOffset
                _ <- Logging.info(s"Effective offsets to retrieve: $off, number of events: $numberOfEventsToTake")
                _ <-
                  if (numberOfEventsToTake > 0) {
                    Consumer.subscribeAnd(Subscription.manual(off.map(_._1).toIndexedSeq: _*))
                      .plainStream(Serde.string, Serde.byteArray).take(numberOfEventsToTake)
                      .mapM(e => (replyTo ! MessageReceived(topic = topic, committableRecord = e)).as(e))
                      .map(_.offset).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain
                  } else { RIO.effect(Chunk.empty) }
              } yield ()
            } else { ZIO.unit }
          _ <- Logging.info(s"Done collecting events.")
        } yield res
      }.onError(err => CompetitionLogging.logError(err.squashTrace)).provideSomeLayer[Clock with Blocking with Logging](
        Consumer.make(ConsumerSettings(brokers).withGroupId(groupId).withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(
          Consumer.AutoOffsetStrategy.Earliest
        ))).toLayer
      )

    }
}
