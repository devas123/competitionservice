package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.dungeon.SystemMessage
import zio.{Hub, ZIO}

sealed trait EventStreamMessage[+A]
case class SystemEvent(systemEvent: SystemMessage[Any]) extends EventStreamMessage[Unit]
case class DeadLetter(message: Any) extends EventStreamMessage[Unit]
case class CustomMessage(message: Any) extends EventStreamMessage[Unit]

case class EventStream(private val hub: Hub[EventStreamMessage[Any]]) {
  def publish(msg: EventStreamMessage[Any]): ZIO[Any, Nothing, Boolean]               = hub.publish(msg)
  def publishAll(msgs: Iterable[EventStreamMessage[Any]]): ZIO[Any, Nothing, Boolean] = hub.publishAll(msgs)
//  def subscribe[Classifier[_]](subscriber: ActorRef)(implicit transform: A ~> Option[Classifier]) : ZManaged[Any, Nothing, ZStream[Any, Nothing, Classifier[Any]]] = {
//    import zio.stream._
//    hub.subscribe.map(_.filterOutput { m =>
//      transform(m) match {
//        case Some(value) => ???
//        case None => ???
//      }
//    }.map(_.asInstanceOf[Classifier[Any]]))
//      .map(ZStream.fromQueue(_))
//  }
}
