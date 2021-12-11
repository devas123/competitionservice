package compman.compsrv.logic.actors.behavior

import compman.compsrv.logic.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.model.events.EventDTO
import zio.{Queue, Tag, Task}
import zio.clock.Clock
import zio.logging.Logging

object WebsocketConnectionSupervisor {

  sealed trait ApiCommand

  final case class WebsocketConnectionRequest(clientId: String, competitionId: String, queue: Queue[EventDTO])
    extends ApiCommand

  final case class WebsocketConnectionClosed(clientId: String, competitionId: String) extends ApiCommand

  final case class EventReceived(event: EventDTO) extends ApiCommand

  case class ActorState()

  private val CONNECTION_HANDLER_PREFIX = "ConnectionHandler-"

  val initialState: ActorState = ActorState()

  def behavior[R: Tag]: ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] = {
    (context: Context[ApiCommand], _: ActorConfig, state: ActorState, command: ApiCommand, _: Timers[R with Logging with Clock, ApiCommand]) => {
      for {
        res <- command match {
          case EventReceived(event) =>
            val competitionId = event.getCompetitionId
            val handlerName = CONNECTION_HANDLER_PREFIX + competitionId
            for {
              childOption <- context.findChild[WebsocketConnection.ApiCommand](handlerName)
              _ <- childOption match {
                case Some(value) => Logging.info(s"Forwarding event $event to ws") *> (value ! WebsocketConnection.ReceivedEvent(event))
                case None => Logging.info(s"Did not find any ws connection for competition ${event.getCompetitionId}")
              }
            } yield state
          case WebsocketConnectionRequest(clientId, competitionId, queue) =>
            val handlerName = CONNECTION_HANDLER_PREFIX + competitionId
            for {
              _ <- Logging.info(s"New connection request for competition $competitionId, client id: $clientId")
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
            } yield state
          case WebsocketConnectionClosed(clientId, competitionId) =>
            val handlerName = CONNECTION_HANDLER_PREFIX + competitionId
            for {
              _ <- Logging.info(s"Websocket connection closed for $competitionId, client id: $clientId")
              childOption <- context.findChild[WebsocketConnection.ApiCommand](handlerName)
              _ <- childOption match {
                case Some(child) => child ! WebsocketConnection.WebSocketConnectionTerminated(clientId)
                case None => Task.unit
              }
            } yield state
        }
      } yield res
    }
  }
}
