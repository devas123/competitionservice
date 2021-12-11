package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.ActorRef
import zio.{Ref, Task}

trait DeathWatch[Msg] {
  final def watchWith(self: ActorRef[Msg])(
    watching: Ref[Map[ActorRef[Any], Option[Any]]],
    watchedBy: Ref[Set[ActorRef[Any]]]
  )(subject: ActorRef[Any], message: Option[Any]): Task[Unit] = {
    watching.update { w =>
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
      updated
    }
  }

  final def unwatch[F](
    self: ActorRef[Msg]
  )(watching: Ref[Map[ActorRef[Any], Option[Any]]])(subject: ActorRef[Any]): Task[Unit] = {
    watching.update { w =>
      val updated =
        if (subject != self) { w - subject }
        else { w }
      updated
    }
  }

  final def watch(
    self: ActorRef[Msg]
  )(watching: Ref[Map[ActorRef[Any], Option[Any]]], watchedBy: Ref[Set[ActorRef[Any]]])(
    subject: ActorRef[Any]
  ): Task[Unit] = watchWith(self)(watching, watchedBy)(subject, None)

  private[actors] def terminatedQueuedFor(
    terminatedQueued: Ref[Map[ActorRef[Any], Option[Any]]]
  )(subject: ActorRef[Any], customMessage: Option[Any]): Task[Unit] = {
    terminatedQueued.update { tq => if (!tq.contains(subject)) tq + (subject -> customMessage) else tq }
  }
}
