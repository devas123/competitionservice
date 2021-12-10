package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.ActorRef
import zio.{Ref, Task}

trait DeathWatch[Msg[+_]] {
  final def watchWith[F[+_]](self: ActorRef[Msg])(
    watching: Ref[Map[Any, Option[Any]]],
    watchedBy: Ref[Set[Any]]
  )(subject: ActorRef[F], message: Option[Any]): Task[ActorRef[F]] = {
    watching.modify { w =>
      val updated =
        if (subject != self) {
          if (!w.contains(subject)) { w + (subject -> message) }
          else {
            val previous = w(subject)
            if (previous != message) {
              throw new IllegalStateException(
                s"Watch($self, $subject) termination message was not overwritten from [$previous] to [$message]. " +
                  s"If this was intended, unwatch first before using `watch` / `watchWith` with another message."
              )
            }
            w
          }
        } else { w }
      (subject, updated)
    }
  }

  final def unwatch[F[+_]](
    self: ActorRef[Msg]
  )(watching: Ref[Map[Any, Option[Any]]])(subject: ActorRef[F]): Task[ActorRef[F]] = {
    watching.modify { w =>
      val updated =
        if (subject != self) { w - subject }
        else { w }
      (subject, updated)
    }
  }

  final def watch[F[+_]](
    self: ActorRef[Msg]
  )(watching: Ref[Map[Any, Option[Any]]], watchedBy: Ref[Set[Any]])(
    subject: ActorRef[F]
  ): Task[ActorRef[F]] = watchWith(self)(watching, watchedBy)(subject, None)

  private[actors] def terminatedQueuedFor[F[+_]](
    terminatedQueued: Ref[Map[Any, Option[Any]]]
  )(subject: ActorRef[F], customMessage: Option[Any]): Task[Unit] = {
    terminatedQueued.update { tq => if (!tq.contains(subject)) tq + (subject -> customMessage) else tq }
  }
}
