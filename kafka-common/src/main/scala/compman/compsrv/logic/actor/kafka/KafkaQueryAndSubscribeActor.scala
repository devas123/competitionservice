package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.logError
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import zio.{Promise, RIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.kafka.consumer._
import zio.kafka.consumer.Consumer.AutoOffsetStrategy.Latest
import zio.kafka.serde.Serde
import zio.logging.Logging

import java.util.UUID
import scala.util.{Failure, Success, Try}

private[kafka] object KafkaQueryAndSubscribeActor {

  def apply(name: String, actorRefProvider: ActorRefProvider)(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    brokers: List[String],
    subscribe: Boolean,
    query: Boolean,
    startOffset: Option[Long],
    endOffset: Option[Long]
  ): ZIO[Logging with Clock with Blocking with Console, Throwable, ActorRef[KafkaQueryActorCommand]] = for {
    initState <- Promise.make[Throwable, Boolean]
    startFixed = startOffset.map(so => java.lang.Long.max(so, 0L).longValue())
    endFixed   = endOffset.flatMap(e => startFixed.map(sf => java.lang.Long.max(sf, e).longValue()))
    actor <- actorRefProvider.make(
      name,
      ActorConfig(),
      initState,
      behavior(topic, groupId, replyTo, brokers, subscribe, query, startFixed, endFixed)
    )
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
    startOffset: Option[Long],
    endOffset: Option[Long]
  ): ActorBehavior[Logging with Clock with Blocking, Promise[Throwable, Boolean], KafkaQueryActorCommand] = {

    def queryAndSendEvents(): ZIO[Clock with Blocking with Logging, Throwable, Unit] = {
      for {
        _      <- replyTo ! QueryStarted()
        result <- retrieveEvents().fold(e => QueryError(e), QueryFinished)
        _      <- Logging.info(s"Done collecting events. Result: $result")
        _      <- replyTo ! result
      } yield ()
    }

    def startByteArrayStream(
      action: CommittableRecord[String, Try[Array[Byte]]] => ZIO[Logging, Throwable, Unit],
      stopSignal: Promise[Throwable, Boolean]
    ): ZIO[Logging with Clock with Blocking, Throwable, Unit] = {
      Logging.info(s"Starting byte array stream for topic $topic, group id: $groupId") *>
        Consumer.subscribeAnd(Subscription.topics(topic)).plainStream(Serde.string, Serde.byteArray.asTry)
          .mapM(record => action(record)).haltWhen(stopSignal).runDrain *>
        Logging.info(s"Finished listening on topic $topic, group id: $groupId")
    }.provideSomeLayer[Logging with Clock with Blocking](
      Consumer.make(ConsumerSettings(brokers).withGroupId(groupId).withOffsetRetrieval(offsetRetrieval(startOffset)))
        .toLayer
    )

    def retrieveEvents(): RIO[Clock with Blocking with Logging, Long] = {
      for {
        partitions <- Consumer.partitionsFor(topic)
        topicPartitions = partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet
        kafkaTopicEndOffsetsMap <- Consumer.endOffsets(topicPartitions)
        partitionsToEndOffsetsMap = kafkaTopicEndOffsetsMap.map { case (top, offset) =>
          (top, endOffset.getOrElse(offset))
        }
        filteredOffsets = partitionsToEndOffsetsMap.filter(_._2 > startOffset.getOrElse(0L))
        _ <- Logging.info(
          s"Getting events from topic $topic, endOffset: $endOffset, endOffsets: $partitionsToEndOffsetsMap, start from $startOffset"
        )
        res <-
          if (filteredOffsets.nonEmpty) for {
            _ <- Logging.info(s"Filtered offsets: $filteredOffsets")
            off = filteredOffsets.keySet.map(tp => {
              val partition = tp
              ((tp.topic(), tp.partition()), partitionsToEndOffsetsMap(partition))
            }).filter(o => o._2 > 0)
            numberOfEventsToTake = off.foldLeft(0L)((acc, el) => acc + el._2) - startOffset.getOrElse(0L)
            _ <- Logging
              .info(s"Effective offsets to retrieve: ${off.map(_._1)}, number of events: $numberOfEventsToTake")
            collectedCount <-
              if (numberOfEventsToTake > 0) {
                Consumer.subscribeAnd(Subscription.manual(off.map(_._1).toIndexedSeq: _*))
                  .plainStream(Serde.string, Serde.byteArray).take(numberOfEventsToTake)
                  .mapM(e => (replyTo ! MessageReceived(topic = topic, committableRecord = e)).as(e)).runCount
              } else { RIO.effect(0L) }
          } yield collectedCount
          else RIO.effect(0L)
      } yield res
    }.onError(err => CompetitionLogging.logError(err.squashTrace)).provideSomeLayer[Clock with Blocking with Logging](
      Consumer.make(ConsumerSettings(brokers).withGroupId(UUID.randomUUID().toString).withOffsetRetrieval(
        offsetRetrieval(startOffset)
      )).toLayer
    )

    Behaviors.behavior[Logging with Clock with Blocking, Promise[Throwable, Boolean], KafkaQueryActorCommand]
      .withReceive { (context, _, state, command, _) =>
        command match {
          case ForwardMesage(msg, to) => Logging.info(s"Forwarding message to actor: $to") *> (to ! msg).as(state)
          case Stop                   => state.succeed(true) *> context.stopSelf.as(state)
        }
      }.withPostStop { (_, context, state, _) =>
        Logging.info(s"Stopping kafka Query and Subscribe actor ${context.self}.") *> state.succeed(true).unit
      }.withInit { (_, context, state, _) =>
        {
          for {
            _ <- context.watchWith(Stop, replyTo)
            _ <- queryAndSendEvents().when(query)
            _ <- (for {
              _ <- startByteArrayStream(
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
                      Logging.info(s"Sending a message from stream to $replyTo") *>
                        (context.self !
                          ForwardMesage(MessageReceived(topic, record.copy(record = newConsumerRecord)), replyTo))
                  }
                },
                state
              ).when(subscribe)
              _ <- Logging.info("Finished consuming from kafka. Stopping.")
            } yield ()).fork
          } yield (Seq.empty, Seq.empty, state)
        }
      }
  }

  private def offsetRetrieval(startOffset: Option[Long]) = startOffset match {
    case Some(value) => Consumer.OffsetRetrieval.Manual(topics => ZIO.effectTotal(topics.map((_, value)).toMap))
    case None        => Consumer.OffsetRetrieval.Auto(Latest)
  }
}
