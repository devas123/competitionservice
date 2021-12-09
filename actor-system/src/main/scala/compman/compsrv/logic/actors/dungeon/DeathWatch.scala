package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.ActorRef
import zio.{Ref, Task}

trait DeathWatch {
  self: ActorRef[_] =>
  var watching: Ref[Map[ActorRef[_], Option[Any]]]
  var watchedBy: Ref[Set[ActorRef[_]]]
  var terminatedQueued: Ref[Map[ActorRef[_], Option[Any]]]

  final def watchWith[F[+_]](subject: ActorRef[F], message: Option[Any]): Task[ActorRef[F]] = {
    watching.modify { w =>
      if (subject != self) {
        if (!w.contains(subject)) {
          w + (subject -> message)
        } else {
          val previous = w(subject)
          if (previous != message) {
            throw new IllegalStateException(
              s"Watch($self, $subject) termination message was not overwritten from [$previous] to [$message]. " +
                s"If this was intended, unwatch first before using `watch` / `watchWith` with another message.")
          }
        }
      }
      (subject, w)
    }
  }

  final def watch[F[+_]](subject: ActorRef[F]): Task[ActorRef[F]] = watchWith(subject, None)

  private[actors] def terminatedQueuedFor[F[+_]](subject: ActorRef[F], customMessage: Option[Any]): Task[Unit] = {
    terminatedQueued.update { tq =>
      if (!tq.contains(subject))
        tq + (subject -> customMessage)
      else tq
    }
  }
}
