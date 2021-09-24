package compman.compsrv.query.actors.behavior

import compman.compsrv.model.events.EventDTO
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{Queue, RIO, Tag, ZIO}
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging.Logging

object WebsocketConnection {

  sealed trait ApiCommand[+_]

  final case class AddWebSocketConnection(clientId: String, queue: Queue[EventDTO]) extends ApiCommand[Unit]
  final case class WebSocketConnectionTerminated(clientId: String)                  extends ApiCommand[Unit]
  final case class ReceivedEvent(event: EventDTO)                                   extends ApiCommand[Unit]
  final case object Stop                                                            extends ApiCommand[Unit]

  case class ActorState(queues: Map[String, Queue[EventDTO]])

  val initialState: ActorState = ActorState(Map.empty)

  def behavior[R: Tag]: ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] = {
    new ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] {

      override def receive[A](
        context: Context[ApiCommand],
        actorConfig: ActorConfig,
        state: ActorState,
        command: ApiCommand[A],
        timers: Timers[R with Logging with Clock, ApiCommand]
      ): RIO[R with Logging with Clock, (ActorState, A)] = {
        val stopTimerKey = "StopTimer"
        for {
          _ <- Logging.info(s"Received API command $command")
          res <- command match {
            case AddWebSocketConnection(clientId, queue) => for {
                _ <- timers.cancelTimer(stopTimerKey)
              } yield (state.copy(queues = state.queues + (clientId -> queue)), ().asInstanceOf[A])
            case ReceivedEvent(event) =>
              import cats.implicits._
              import zio.interop.catz._
              for { _ <- state.queues.values.toList.traverse(q => q.offer(event)) } yield (state, ().asInstanceOf[A])
            case WebSocketConnectionTerminated(clientId) => for {
                q <- ZIO.effect(state.queues.get(clientId))
                _ <- q match {
                  case Some(value) => value.shutdown
                  case None        => ZIO.unit
                }
                newQueues <- ZIO.effect(state.queues - clientId)
                _         <- if (newQueues.isEmpty) timers.startSingleTimer(stopTimerKey, 10.seconds, Stop) else ZIO.unit
              } yield (state.copy(queues = state.queues - clientId), ().asInstanceOf[A])
            case Stop => for { _ <- context.self.stop } yield (state, ().asInstanceOf[A])
          }
        } yield res
      }
    }
  }
}
