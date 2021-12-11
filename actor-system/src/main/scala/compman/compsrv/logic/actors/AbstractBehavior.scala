package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon._
import zio.{Ref, RIO, Task}
import zio.clock.Clock

trait AbstractBehavior[R, S, Msg] {
  self: DeathWatch[Msg] =>

  def makeActor(
    actorPath: ActorPath,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Set[ActorRef[Any]]]
  )(postStop: () => Task[Unit]): RIO[R with Clock, ActorRef[Msg]]

  private[actors] def processSystemMessage(
                                            context: Context[Msg],
                                            watching: Ref[Map[ActorRef[Any], Option[Any]]],
                                            watchedBy: Ref[Set[ActorRef[Any]]]
  )(systemMessage: SystemMessage): RIO[R, Unit] = systemMessage match {
    case Watch(watchee, watcher, msg) =>
      if (watcher == context.self) self.watchWith(context.self)(watching, watchedBy)(watchee, msg).unit
      else Task.fail(new IllegalStateException())
    case Unwatch(watchee, watcher) =>
      if (watcher == context.self) self.unwatch(context.self)(watching)(watchee).unit
      else Task.fail(new IllegalStateException())
    case DeathWatchNotification(actor) => for {
        iAmWatching <- watching.get
        _ <- iAmWatching.get(actor) match {
          case Some(value) => context.self ! value.asInstanceOf[Msg]
          case None        => context.self sendSystemMessage Terminated(actor)
        }
        _ <- watching.set(iAmWatching - actor)
        _ <- watchedBy.update(_ - actor)
      } yield ()
    case _ => RIO.unit
  }

}
