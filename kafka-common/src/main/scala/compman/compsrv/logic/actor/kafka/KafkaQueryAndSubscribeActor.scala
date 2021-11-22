package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import org.apache.kafka.common.TopicPartition
import zio.{Chunk, Fiber, RIO, Tag, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer._
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.stream.ZStream

object KafkaQueryAndSubscribeActor {
  sealed trait KafkaQueryActorCommand[+_]

  case object Stop extends KafkaQueryActorCommand[Unit]

  def behavior[R: Tag](
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    brokers: List[String],
    subscribe: Boolean,
    query: Boolean
  ): ActorBehavior[R with Logging with Clock with Blocking, Unit, KafkaQueryActorCommand] =
    new ActorBehavior[R with Logging with Clock with Blocking, Unit, KafkaQueryActorCommand] {
      override def receive[A](
                               context: Context[KafkaQueryActorCommand],
                               actorConfig: ActorConfig,
                               state: Unit,
                               command: KafkaQueryActorCommand[A],
                               timers: Timers[R with Logging with Clock with Blocking, KafkaQueryActorCommand]
      ): RIO[R with Logging with Clock with Blocking, (Unit, A)] =
        command match { case Stop => context.stopSelf.as(((), ().asInstanceOf[A])) }

      import cats.implicits._
      import zio.interop.catz._
      override def init(
                         actorConfig: ActorConfig,
                         context: Context[KafkaQueryActorCommand],
                         initState: Unit,
                         timers: Timers[R with Logging with Clock with Blocking, KafkaQueryActorCommand]
      ): RIO[R with Logging with Clock with Blocking, (Seq[Fiber[Throwable, Unit]], Seq[KafkaQueryActorCommand[Any]])] = {
        (for {
          _ <- super.init(actorConfig, context, initState, timers)
          _ <-
            if (query) {
              for {
                partitions <- Consumer.partitionsFor(topic)
                endOffsets <- Consumer
                  .endOffsets(partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet)
                _      <- replyTo ! QueryStarted()
                events <- retrieveEvents(topic, endOffsets)
                queryResult <- events.traverse(e => replyTo ! MessageReceived(topic = topic, committableRecord = e))
                  .fold(e => QueryError(e), _ => QueryFinished())
                _ <- replyTo ! queryResult
              } yield ()
            } else { ZIO.unit }
          fiber <-
            if (subscribe) { getByteArrayStream(topic).runDrain.fork }
            else { ZIO.unit.fork }
          _ <- fiber.join
          _ <- context.self ! Stop
        } yield (Seq.empty, Seq.empty)).provideSomeLayer[R with Logging with Clock with Blocking](
          Consumer.make(ConsumerSettings(brokers).withGroupId(groupId).withOffsetRetrieval(
            Consumer.OffsetRetrieval.Auto(Consumer.AutoOffsetStrategy.Earliest)
          )).toLayer
        )
      }

      def getByteArrayStream(
        topic: String
      ): ZStream[Clock with Blocking with Logging with Consumer, Throwable, CommittableRecord[String, Array[Byte]]] = {
        Consumer.subscribeAnd(Subscription.topics(topic)).plainStream(Serde.string, Serde.byteArray)
      }

      def retrieveEvents(
        topic: String,
        endOffsets: Map[TopicPartition, Long]
      ): RIO[Clock with Blocking with Logging with Consumer, List[CommittableRecord[String, Array[Byte]]]] = for {
        offset          <- Consumer.beginningOffsets(endOffsets.keySet)
        filteredOffsets <- RIO(endOffsets.filter(_._2 > 0))
        _ <- Logging.info(s"Getting events from topic $topic, endOffsets: $endOffsets, start from $offset")
        res <-
          if (filteredOffsets.nonEmpty) {
            for {
              _ <- Logging.info(s"Filtered offsets: $filteredOffsets")
              off = filteredOffsets.keySet.map(tp => {
                val partition = tp
                ((tp.topic(), tp.partition()), endOffsets(partition) - offset(partition))
              }).filter(o => o._2 > 0)
              _ <- Logging.info(s"Effective offsets to retrieve: $off")
              numberOfEventsToTake = off.foldLeft(0L)((acc, el) => acc + el._2)
              res1 <-
                if (numberOfEventsToTake > 0) {
                  Consumer.subscribeAnd(Subscription.manual(off.map(_._1).toIndexedSeq: _*))
                    .plainStream(Serde.string, Serde.byteArray).take(numberOfEventsToTake).runCollect
                } else { RIO.effect(Chunk.empty) }
              _ <- res1.map(_.offset).foldLeft(OffsetBatch.empty)(_ merge _).commit
            } yield res1.toList
          } else { ZIO.effectTotal(List.empty) }
        _ <- Logging.info("Done collecting events.")
      } yield res
    }
}
