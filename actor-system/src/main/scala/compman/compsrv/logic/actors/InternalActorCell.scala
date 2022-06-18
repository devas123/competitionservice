package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.dungeon.SystemMessage
import zio.Task

private[actors] case class InternalActorCell[-F](actor: ActorRef[F])
    extends ActorRef[F] {
  override private[actors] def sendSystemMessage(msg: SystemMessage) = actor.sendSystemMessage(msg)
  override def !(fa: F): Task[Unit]                                  = actor ! fa
  override private[actors] val stop                                  = for {
    res <- actor.stop
  } yield res

  override def hashCode(): Int = actor.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case x: InternalActorCell[_] => actor.equals(x.actor)
    case x: ActorRef[_]          => actor.equals(x)
    case _                       => false
  }

  override def toString: String = actor.toString
}
