package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.{RIO, Tag}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.CommittableRecord
import zio.logging.Logging

import java.util.UUID

object KafkaSupervisor {
  sealed trait KafkaConsumerApi[+_]
  final case class QueryStarted()               extends KafkaConsumerApi[Unit]
  final case class QueryFinished()              extends KafkaConsumerApi[Unit]
  final case class QueryError(error: Throwable) extends KafkaConsumerApi[Unit]
  final case class MessageReceived(topic: String, committableRecord: CommittableRecord[String, Array[Byte]])
      extends KafkaConsumerApi[Unit]
  sealed trait KafkaSupervisorCommand[+_]
  case class QueryAndSubscribe(topic: String, groupId: String, replyTo: ActorRef[KafkaConsumerApi])
      extends KafkaSupervisorCommand[Unit]
  case class Query(topic: String, groupId: String, replyTo: ActorRef[KafkaConsumerApi])
      extends KafkaSupervisorCommand[Unit]
  case class Subscribe(topic: String, groupId: String, replyTo: ActorRef[KafkaConsumerApi])
      extends KafkaSupervisorCommand[Unit]

  case object Stop extends KafkaSupervisorCommand[Unit]

  def behavior[R: Tag](
    brokers: List[String]
  ): ActorBehavior[R with Logging with Clock with Blocking, Unit, KafkaSupervisorCommand] =
    new ActorBehavior[R with Logging with Clock with Blocking, Unit, KafkaSupervisorCommand] {
      override def receive[A](
        context: Context[KafkaSupervisorCommand],
        actorConfig: ActorConfig,
        state: Unit,
        command: KafkaSupervisorCommand[A],
        timers: Timers[R with Logging with Clock with Blocking, KafkaSupervisorCommand]
      ): RIO[R with Logging with Clock with Blocking, (Unit, A)] = command match {
        case QueryAndSubscribe(topic, groupId, replyTo) => context.make(
            UUID.randomUUID().toString,
            ActorConfig(),
            (),
            KafkaQueryAndSubscribeActor.behavior(topic, groupId, replyTo, brokers, subscribe = true, query = true)
          ).as(((), ()).asInstanceOf[(Unit, A)])
        case Query(topic, groupId, replyTo) => context.make(
            UUID.randomUUID().toString,
            ActorConfig(),
            (),
            KafkaQueryAndSubscribeActor.behavior(topic, groupId, replyTo, brokers, subscribe = false, query = true)
          ).as(((), ()).asInstanceOf[(Unit, A)])
        case Subscribe(topic, groupId, replyTo) => context.make(
          UUID.randomUUID().toString,
          ActorConfig(),
          (),
          KafkaQueryAndSubscribeActor.behavior(topic, groupId, replyTo, brokers, subscribe = true, query = false)
        ).as(((), ()).asInstanceOf[(Unit, A)])
        case Stop => context.stopSelf.map(_.asInstanceOf[(Unit, A)])
      }
    }
}
