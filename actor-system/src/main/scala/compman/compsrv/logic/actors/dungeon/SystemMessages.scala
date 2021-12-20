package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.ActorRef

private[actors] sealed trait SystemMessage

sealed trait Signal extends SystemMessage

private[actors] final case class Watch(watchee: ActorRef[Nothing], watcher: ActorRef[Nothing], msg: Option[Any])
  extends SystemMessage // sent to establish a DeathWatch

private[actors] final case class Unwatch(watchee: ActorRef[Nothing], watcher: ActorRef[Nothing])
  extends SystemMessage // sent to establish a DeathWatch

private[actors] final case class DeathWatchNotification(actor: ActorRef[Nothing]) extends SystemMessage

final case class DeadLetter(message: Any, sender: Option[ActorRef[Nothing]], receiver: ActorRef[Nothing]) extends SystemMessage

final case class Terminated(ref: ActorRef[Nothing]) extends SystemMessage with Signal

final case class Suppressed(e: Any) extends SystemMessage with Signal

case object PoisonPill extends SystemMessage with Signal
