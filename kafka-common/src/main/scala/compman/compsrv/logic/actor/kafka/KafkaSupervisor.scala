package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.{RIO, Tag}
import zio.clock.Clock
import zio.kafka.consumer.CommittableRecord
import zio.logging.Logging

object KafkaSupervisor {
  sealed trait KafkaConsumerApi[+_]
  final case class QueryStarted() extends KafkaConsumerApi[Unit]
  final case class QueryFinished() extends KafkaConsumerApi[Unit]
  final case class QueryError(error: Throwable) extends KafkaConsumerApi[Unit]
  final case class MessageReceived(topic: String, committableRecord: CommittableRecord[String, Array[Byte]]) extends KafkaConsumerApi[Unit]
  sealed trait KafkaSupervisorCommand[+_]
  case class QueryAndSubscribe(topic: String, groupId: String) extends KafkaSupervisorCommand[Unit]

  case object Stop                                                  extends KafkaSupervisorCommand[Unit]

  def behavior[R: Tag](
                      ): ActorBehavior[R with Logging with Clock, Unit, KafkaSupervisorCommand] =
    new ActorBehavior[R with Logging with Clock, Unit, KafkaSupervisorCommand] {
      override def receive[A](context: Context[KafkaSupervisorCommand], actorConfig: ActorConfig, state: Unit, command: KafkaSupervisorCommand[A], timers: Timers[R with Logging with Clock, KafkaSupervisorCommand]): RIO[R with Logging with Clock, (Unit, A)] = command match {
        case QueryAndSubscribe(topic, groupId) => ???

        case Stop => context.stopSelf.map(_.asInstanceOf[(Unit, A)])
      }
    }
}
