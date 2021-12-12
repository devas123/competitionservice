package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.{ActorBehavior, Behaviors}
import zio.{Promise, RIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.Duration
import zio.logging.Logging

import scala.concurrent.TimeoutException

object KafkaSyncQueryReceiverActor {

  import Behaviors._
  def behavior(
    promise: Promise[Throwable, Seq[Array[Byte]]],
    timeout: Duration
  ): ActorBehavior[Logging with Clock with Blocking, Seq[Array[Byte]], KafkaConsumerApi] = Behaviors
    .behavior[Logging with Clock with Blocking, Seq[Array[Byte]], KafkaConsumerApi]
    .withReceive { (context, _, state, command, _) =>
      command match {
        case QueryStarted() => RIO(state)
        case QueryFinished() => Logging.error(s"Successfully finished the query. Stopping.") *>
            promise.succeed(state) *> context.stopSelf *> RIO(state)
        case QueryError(error) => Logging.error(s"Error during kafka query: $error") *> promise.succeed(state) *>
            context.stopSelf *> RIO(state)
        case MessageReceived(_, committableRecord) => RIO(state :+ committableRecord.value)
      }
    }.withInit { (_, _, initState, timers) =>
      timers.startSingleTimer("timeout", timeout, QueryError(new TimeoutException(s"Query timeout: $timeout")))
        .as((Seq.empty, Seq.empty, initState))
    }

}
