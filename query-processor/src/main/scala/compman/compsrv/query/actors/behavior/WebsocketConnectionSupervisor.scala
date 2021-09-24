package compman.compsrv.query.actors.behavior

import compman.compsrv.model.events.EventDTO
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{Queue, RIO, Tag, Task}
import zio.clock.Clock
import zio.logging.Logging

object WebsocketConnectionSupervisor {

  sealed trait ApiCommand[+_]

  final case class WebsocketConnectionRequest(clientId: String, competitionId: String, queue: Queue[EventDTO])
      extends ApiCommand[Unit]
  final case class WebsocketConnectionClosed(clientId: String, competitionId: String) extends ApiCommand[Unit]

  case class ActorState()

  private val CONNECTION_HANDLER_PREFIX = "ConnectionHandler-"

  val initialState: ActorState = ActorState()

  def behavior[R: Tag]: ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] = {
    new ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] {
      override def receive[A](
        context: Context[ApiCommand],
        actorConfig: ActorConfig,
        state: ActorState,
        command: ApiCommand[A],
        timers: Timers[R with Logging with Clock, ApiCommand]
      ): RIO[R with Logging with Clock, (ActorState, A)] = {
        for {
          _ <- Logging.info(s"Received API command $command")
          res <- command match {
            case WebsocketConnectionRequest(clientId, competitionId, queue) =>
              val handlerName = CONNECTION_HANDLER_PREFIX + competitionId
              for {
                childOption <- context.findChild[WebsocketConnection.ApiCommand](handlerName)
                child <- childOption match {
                  case Some(value) => Task(value)
                  case None => for {
                      c <- context.make(
                        handlerName,
                        ActorConfig(),
                        WebsocketConnection.initialState,
                        WebsocketConnection.behavior
                      )
                    } yield c
                }
                _ <- child ! WebsocketConnection.AddWebSocketConnection(clientId, queue)
              } yield (state, ().asInstanceOf[A])
            case WebsocketConnectionClosed(clientId, competitionId) =>
              val handlerName = CONNECTION_HANDLER_PREFIX + competitionId
              for {
                childOption <- context.findChild[WebsocketConnection.ApiCommand](handlerName)
                _ <- childOption match {
                  case Some(child) => child ! WebsocketConnection.WebSocketConnectionTerminated(clientId)
                  case None        => Task.unit
                }
              } yield (state, ().asInstanceOf[A])
          }
        } yield res
      }
    }
  }
}
