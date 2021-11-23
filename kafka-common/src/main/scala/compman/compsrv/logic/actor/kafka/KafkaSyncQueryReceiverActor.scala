package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.{ActorBehavior, Context, Timers}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.Duration
import zio.logging.Logging
import zio.{Fiber, Promise, RIO}

import scala.concurrent.TimeoutException

object KafkaSyncQueryReceiverActor {

  def behavior(
                        promise: Promise[Throwable, Seq[Array[Byte]]],
                        timeout: Duration
                      ): ActorBehavior[Logging with Clock with Blocking, Seq[Array[Byte]], KafkaConsumerApi] =
    new ActorBehavior[Logging with Clock with Blocking, Seq[Array[Byte]], KafkaConsumerApi] {
      override def receive[A](
                               context: Context[KafkaConsumerApi],
                               actorConfig: ActorConfig,
                               state: Seq[Array[Byte]],
                               command: KafkaConsumerApi[A],
                               timers: Timers[Logging with Clock with Blocking, KafkaConsumerApi]
                             ): RIO[Logging with Clock with Blocking, (Seq[Array[Byte]], A)] =
        command match {
          case QueryStarted() => RIO((state, ().asInstanceOf[A]))
          case QueryFinished() => promise.succeed(state) *> context.stopSelf.as((state, ().asInstanceOf[A]))
          case QueryError(error) => Logging.error(s"Error during kafka query: $error") *> promise.succeed(state) *> context.stopSelf.as((state, ().asInstanceOf[A]))
          case MessageReceived(_, committableRecord) => RIO((state :+ committableRecord.value, ().asInstanceOf[A]))
        }

      override def init(actorConfig: ActorConfig, context: Context[KafkaConsumerApi], initState: Seq[Array[Byte]], timers: Timers[Logging with Clock with Blocking, KafkaConsumerApi]): RIO[Logging with Clock with Blocking, (Seq[Fiber[Throwable, Unit]], Seq[KafkaConsumerApi[Any]], Seq[Array[Byte]])] =
        timers.startSingleTimer("timeout", timeout, QueryError(new TimeoutException(s"Query timeout: $timeout")))
          .as((Seq.empty, Seq.empty, initState))
    }

}
