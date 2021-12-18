package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon._
import zio.{Ref, RIO, Task, ZIO}
import zio.clock.Clock

trait AbstractBehavior[R, S, Msg] {
  self: DeathWatch =>

  private[actors] def makeActor(
    actorPath: ActorPath,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Set[InternalActorCell[Nothing]]]
  )(postStop: () => Task[Unit]): RIO[R with Clock, InternalActorCell[Msg]]

  private[actors] def processSystemMessage(
    context: Context[Msg],
    watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
    watchedBy: Ref[Set[ActorRef[Nothing]]]
  )(systemMessage: SystemMessage): RIO[R, Unit] = systemMessage match {
    case Watch(watchee, watcher, msg) =>
      if (watcher == context.self) self.watchWith(context.self)(watching)(watchee, msg).unit
      else if (watchee == context.self) watchedBy.update(_ + watcher)
      else Task.fail(new IllegalStateException("Watcher should be me."))
    case Unwatch(watchee, watcher) =>
      if (watcher == context.self) self.unwatch(context.self)(watching)(watchee).unit
      else Task.fail(new IllegalStateException("Watcher should be me."))
    case DeathWatchNotification(actor) => for {
        iAmWatching <- watching.get
        _ <- iAmWatching.get(actor) match {
          case Some(value) => value match {
              case Some(value) => context.self ! value.asInstanceOf[Msg]
              case None        => context.self sendSystemMessage Terminated(actor)
            }
          case None => ZIO.unit
        }
        _ <- watching.set(iAmWatching - actor)
        _ <- watchedBy.update(_ - actor)
      } yield ()
    case x => RIO.debug(s"Unknown system message $x")
  }
}
