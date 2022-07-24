package compman.compsrv.logic.actors.behavior

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop}
import cats.effect.IO
import cats.effect.std.Queue
import compservice.model.protobuf.event.Event
import fs2.concurrent.SignallingRef

object WebSocketSingleConnectionHandler extends WithIORuntime {

  sealed trait WebSocketSingleConnectionHandlerApi
  case class MessageReceived(event: Event) extends WebSocketSingleConnectionHandlerApi
  object Stop                              extends WebSocketSingleConnectionHandlerApi

  def behavior(
    queue: Queue[IO, Event],
    terminatedSignal: SignallingRef[IO, Boolean],
    clientId: String
  ): Behavior[WebSocketSingleConnectionHandlerApi] = Behaviors
    .receive[WebSocketSingleConnectionHandlerApi] { (context, message) =>
      message match {
        case MessageReceived(event) =>
          queue.offer(event).unsafeRunSync()
          Behaviors.same
        case Stop =>
          Behaviors.stopped(() => context.log.info(s"Terminated connection for client $clientId."))
      }
    }.receiveSignal { case (_, signal) =>
      if (signal == PostStop) {
        terminatedSignal.set(true).unsafeRunSync()
      }
      Behaviors.same
    }
}
