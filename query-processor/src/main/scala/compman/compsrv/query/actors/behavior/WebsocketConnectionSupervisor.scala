package compman.compsrv.query.actors.behavior

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.std.Queue
import cats.effect.IO
import compservice.model.protobuf.event.Event
import fs2.concurrent.SignallingRef

object WebsocketConnectionSupervisor {

  sealed trait ApiCommand

  final case class WebsocketConnectionRequest(
    clientId: String,
    competitionId: String,
    queue: Queue[IO, Event],
    terminatedSignal: SignallingRef[IO, Boolean]
  ) extends ApiCommand

  final case class WebsocketConnectionClosed(clientId: String, competitionId: String) extends ApiCommand

  final case class WebsocketCompetitionSupervisorStopped(competitionId: String) extends ApiCommand

  final case class EventReceived(event: Event) extends ApiCommand

  case class ActorState()

  private val CONNECTION_HANDLER_PREFIX = "ConnectionHandler-"

  def behavior(
    competitionConnectionHandlers: Map[String, ActorRef[WebsocketCompetitionConnectionSupervisor.ApiCommand]] =
      Map.empty
  ): Behavior[ApiCommand] = Behaviors.setup { context =>
    def createNewCompetitionWsSupervisor(competitionId: String, handlerName: String) = {
      val actor = context.spawn(WebsocketCompetitionConnectionSupervisor.behavior(competitionId), handlerName)
      context.watchWith(actor, WebsocketCompetitionSupervisorStopped(competitionId))
      actor
    }

    Behaviors.receiveMessage {
      case EventReceived(event) =>
        val competitionId = event.messageInfo.flatMap(_.competitionId).get
        competitionConnectionHandlers.get(competitionId)
          .foreach(h => h ! WebsocketCompetitionConnectionSupervisor.ReceivedEvent(event))
        Behaviors.same
      case WebsocketConnectionRequest(clientId, competitionId, queue, terminatedSignal) =>
        val handlerName = CONNECTION_HANDLER_PREFIX + competitionId
        context.log.info(s"New connection request for competition $competitionId, client id: $clientId")
        val handler = competitionConnectionHandlers
          .getOrElse(competitionId, createNewCompetitionWsSupervisor(competitionId, handlerName))
        handler ! WebsocketCompetitionConnectionSupervisor.AddWebSocketConnection(clientId, queue, terminatedSignal)
        behavior(competitionConnectionHandlers + (competitionId -> handler))
      case WebsocketConnectionClosed(clientId, competitionId) =>
        context.log.info(s"Websocket connection closed for $competitionId, client id: $clientId")
        competitionConnectionHandlers.get(competitionId)
          .foreach(h => h ! WebsocketCompetitionConnectionSupervisor.WebSocketConnectionTerminated(clientId))
        Behaviors.same
      case WebsocketCompetitionSupervisorStopped(competitionId) =>
        behavior(competitionConnectionHandlers - competitionId)
    }
  }
}
