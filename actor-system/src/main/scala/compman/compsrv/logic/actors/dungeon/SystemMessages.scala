package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.ActorRef

private[actors] sealed trait SystemMessage
private[actors] sealed trait Signal
private[actors] final case class Watch[F1, F2](watchee: ActorRef[F1], watcher: ActorRef[F2], msg: Option[Any])
    extends SystemMessage // sent to establish a DeathWatch
private[actors] final case class Unwatch[F1, F2](watchee: ActorRef[F1], watcher: ActorRef[F2])
    extends SystemMessage // sent to establish a DeathWatch
private[actors] final case class DeathWatchNotification[F1](actor: ActorRef[F1]) extends SystemMessage

final case class Terminated[F](ref: ActorRef[F]) extends SystemMessage with Signal
