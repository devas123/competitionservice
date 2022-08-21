package compman.compsrv.query.actors.behavior

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.std.Queue
import cats.effect.IO
import compservice.model.protobuf.event.Event
import fs2.concurrent.SignallingRef

import scala.concurrent.duration.DurationInt

object WebsocketCompetitionConnectionSupervisor {

  sealed trait ApiCommand

  final case class AddWebSocketConnection(
    clientId: String,
    queue: Queue[IO, Event],
    terminatedSignal: SignallingRef[IO, Boolean]
  )                                                                extends ApiCommand
  final case class WebSocketConnectionTerminated(clientId: String) extends ApiCommand
  final case class WebSocketHandlerStopped(clientId: String)       extends ApiCommand
  final case class ReceivedEvent(event: Event)                     extends ApiCommand
  final case class Stop(replyTo: Option[ActorRef[Boolean]] = None) extends ApiCommand

  def updated(
    connections: Map[String, ActorRef[WebSocketSingleConnectionHandler.WebSocketSingleConnectionHandlerApi]],
    competitionId: String
  ): Behavior[ApiCommand] = {

    Behaviors.receive { (context, command) =>
      def removeConnectionHandler(clientId: String): Behavior[ApiCommand] = {
        val newConnections = connections - clientId
        if (newConnections.isEmpty) {
          Behaviors.stopped(() => context.log.info(s"All connections are closed for $competitionId, stopping actor."))
        } else { updated(newConnections, competitionId) }
      }
      command match {
        case AddWebSocketConnection(clientId, queue, terminatedSignal) =>
          context.log.info(s"Add websocket connection $clientId")
          val connectionHandler = context.spawn(
            WebSocketSingleConnectionHandler.behavior(queue, terminatedSignal, clientId),
            s"ws-con-handler_${competitionId}_$clientId"
          )
          context.watchWith(connectionHandler, WebSocketHandlerStopped(clientId))
          updated(connections + (clientId -> connectionHandler), competitionId)
        case ReceivedEvent(event) =>
          context.log.info(s"Forwarding event to subscribers: $command")
          connections.values.foreach(h => h ! WebSocketSingleConnectionHandler.MessageReceived(event))
          Behaviors.same
        case WebSocketConnectionTerminated(clientId) =>
          connections.get(clientId).foreach(handler => handler ! WebSocketSingleConnectionHandler.Stop)
          removeConnectionHandler(clientId)
        case WebSocketHandlerStopped(clientId) => removeConnectionHandler(clientId)
        case Stop(replyTo) =>
          context.log.info(s"Stopping: $command")
          replyTo.foreach(_ ! true)
          Behaviors.stopped
      }
    }
  }

  def behavior(competitionId: String): Behavior[ApiCommand] = Behaviors.supervise(updated(Map.empty, competitionId))
    .onFailure(SupervisorStrategy.restart.withStopChildren(false).withLimit(100, 1.minute))
}
