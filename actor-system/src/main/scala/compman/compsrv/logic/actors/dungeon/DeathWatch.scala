package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.{ActorRef, InternalActorCell}
import zio.{Ref, Task, ZIO}

trait DeathWatch {
  final def watchWith(self: ActorRef[Nothing])(
    watching: Ref[Map[ActorRef[Nothing], Option[Any]]]
  )(subject: ActorRef[Nothing], message: Option[Any]): Task[Unit] = {
    val subj = subject match {
      case InternalActorCell(actor) => actor
      case x                        => x
    }
    for {
      modify <- watching.modify[Boolean] { w =>
        val updated =
          if (subj != self) {
            if (!w.contains(subj)) { (w + (subj -> message)) -> true }
            else {
              val previous = w(subj)
              if (previous != message) {
                throw new IllegalStateException(
                  s"Watch($self, $subj) termination message was not overwritten from [$previous] to [$message]. " +
                    s"If this was intended, unwatch first before using `watch` / `watchWith` with another message."
                )
              }
              w -> false
            }
          } else { w -> false }
        updated.swap
      }
      _ <- subj.sendSystemMessage(Watch(subj, self, message)).when(modify)
    } yield ()
  }.foldM(fail => ZIO.fail(new IllegalStateException(fail.toString)), _ => ZIO.unit)

  final def unwatch[F](
    self: ActorRef[Nothing]
  )(watching: Ref[Map[ActorRef[Nothing], Option[Any]]])(subject: ActorRef[Nothing]): Task[Unit] = {
    watching.update { w =>
      val updated =
        if (subject != self) { w - subject }
        else { w }
      updated
    }
  }

  final def watch(self: ActorRef[Nothing])(watching: Ref[Map[ActorRef[Nothing], Option[Any]]])(
    subject: ActorRef[Nothing]
  ): Task[Unit] = watchWith(self)(watching)(subject, None)

  private[actors] def terminatedQueuedFor(
    terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
  )(subject: ActorRef[Nothing], customMessage: Option[Any]): Task[Unit] = {
    terminatedQueued.update { tq => if (!tq.contains(subject)) tq + (subject -> customMessage) else tq }
  }
}
