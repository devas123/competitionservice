package compman.compsrv.logic.actors.behavior

import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compservice.model.protobuf.event.Event
import zio.{Queue, ZIO}
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging.Logging

object WebsocketConnection {

  sealed trait ApiCommand

  final case class AddWebSocketConnection(clientId: String, queue: Queue[Event]) extends ApiCommand
  final case class WebSocketConnectionTerminated(clientId: String)                  extends ApiCommand
  final case class ReceivedEvent(event: Event)                                   extends ApiCommand
  final case class Stop(replyTo: Option[ActorRef[Boolean]] = None)                  extends ApiCommand

  case class ActorState(queues: Map[String, Queue[Event]])

  val initialState: ActorState = ActorState(Map.empty)

  import Behaviors._

  def behavior: ActorBehavior[Logging with Clock, ActorState, ApiCommand] = {
    Behaviors.behavior[Logging with Clock, ActorState, ApiCommand].withReceive {
      (context, _: ActorConfig, state, command, timers) =>
        {
          val stopTimerKey = "StopTimer"
          for {
            res <- command match {
              case AddWebSocketConnection(clientId, queue) => for {
                  _ <- Logging.info(s"Add websocket connection $command")
                  _ <- timers.cancelTimer(stopTimerKey)
                } yield state.copy(queues = state.queues + (clientId -> queue))
              case ReceivedEvent(event) =>
                import cats.implicits._
                import zio.interop.catz._
                for {
                  _ <- Logging.info(s"Forwarding event to subscribers: $command")
                  _ <- state.queues.values.toList.traverse(q => q.offer(event))
                } yield state
              case WebSocketConnectionTerminated(clientId) => for {
                  _ <- Logging.info(s"Connection terminated $command")
                  q <- ZIO.effect(state.queues.get(clientId))
                  _ <- q match {
                    case Some(value) => value.shutdown
                    case None        => ZIO.unit
                  }
                  newQueues <- ZIO.effect(state.queues - clientId)
                  _ <- timers.startSingleTimer(stopTimerKey, 10.seconds, Stop()).when(newQueues.isEmpty)
                } yield state.copy(queues = state.queues - clientId)
              case Stop(replyTo) =>
                import cats.implicits._
                import zio.interop.catz._
                for {
                  _ <- Logging.info(s"Stopping: $command")
                  _ <- state.queues.values.toList.traverse(_.shutdown)
                  _ <- context.self.stop
                  _ <- replyTo.map(_ ! true).getOrElse(ZIO.unit)
                } yield state
            }
          } yield res
        }
    }
  }
}
