package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.ActorRef

private[actors] sealed trait SystemMessage[+A]
private[actors] final case class Watch[F1[+_], F2[+_]](watchee: ActorRef[F1], watcher: ActorRef[F2]) extends SystemMessage[Unit] // sent to establish a DeathWatch
private[actors] final case class Unwatch[F1[+_], F2[+_]](watchee: ActorRef[F1], watcher: ActorRef[F2]) extends SystemMessage[Unit] // sent to establish a DeathWatch
