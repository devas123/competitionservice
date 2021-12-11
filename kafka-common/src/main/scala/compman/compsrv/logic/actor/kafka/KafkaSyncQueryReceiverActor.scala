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
      override def receive(
                               context: Context[KafkaConsumerApi],
                               actorConfig: ActorConfig,
                               state: Seq[Array[Byte]],
                               command: KafkaConsumerApi,
                               timers: Timers[Logging with Clock with Blocking, KafkaConsumerApi]
                             ): RIO[Logging with Clock with Blocking, Seq[Array[Byte]]] =
        command match {
          case QueryStarted() => RIO(state)
          case QueryFinished() => Logging.error(s"Successfully finished the query. Stopping.") *> promise.succeed(state) *> context.stopSelf *> RIO(state)
          case QueryError(error) => Logging.error(s"Error during kafka query: $error") *> promise.succeed(state) *> context.stopSelf *> RIO(state)
          case MessageReceived(_, committableRecord) => RIO(state :+ committableRecord.value)
        }

      override def init(actorConfig: ActorConfig, context: Context[KafkaConsumerApi], initState: Seq[Array[Byte]], timers: Timers[Logging with Clock with Blocking, KafkaConsumerApi]): RIO[Logging with Clock with Blocking, (Seq[Fiber[Throwable, Unit]], Seq[KafkaConsumerApi], Seq[Array[Byte]])] =
        timers.startSingleTimer("timeout", timeout, QueryError(new TimeoutException(s"Query timeout: $timeout")))
          .as((Seq.empty, Seq.empty, initState))
    }

}
