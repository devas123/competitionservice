package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.dungeon._
import zio.{Exit, Queue, Ref, RIO, Task, URIO, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._

trait AbstractBehavior[R, S, Msg] {
  self: DeathWatch =>

  private[actors] def makeActor(
                                 actorPath: ActorPath,
                                 actorConfig: ActorConfig,
                                 initialState: S,
                                 actorSystem: ActorSystem,
                                 children: Ref[ContextState]
                               )(postStop: () => Task[Unit]): RIO[R with Clock with Console, InternalActorCell[Msg]]

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
          case None => context.self sendSystemMessage Terminated(actor)
        }
        case None => ZIO.unit
      }
      _ <- watching.set(iAmWatching - actor)
      _ <- watchedBy.update(_ - actor)
    } yield ()
    case x => RIO.debug(s"Unknown system message $x")
  }

  def restartOneSupervision(context: Context[Msg], queue: Queue[PendingMessage[Msg]], timers: Timers[R, Msg])(receiveLoop: () => RIO[R with Clock with Console, Unit]): RIO[R with Clock with Console, Unit] = {
    for {
      _ <- for {
        res <- receiveLoop().onError(err => RIO.debug(s"Error while executing receive loop in actor ${context.self}. $err")).exitCode
        _ <- restartOneSupervision(context, queue, timers)(receiveLoop).unlessM(ZIO.effectTotal(res.code == 0) || queue.isShutdown)
      } yield ()
      _ <- timers.cancelAll()
    } yield ()
  }

  def innerLoop(process: PendingMessage[Msg] => RIO[R with Clock with Console, Unit])(queue: Queue[PendingMessage[Msg]]): RIO[R with Clock with Console, Unit] = {
    (for {
      msg <- queue.take.run
      _ <- msg match {
        case Exit.Success(value) => value match {
          case Left(leftVal) => leftVal match {
            case PoisonPill => queue.shutdown
            case _ => process(value)
          }
          case _ => process(value)
        }
        case Exit.Failure(error) => RIO.debug(s"Actor failed while waiting for message: $error").unit
      }
    } yield ()).repeatUntilM(_ => queue.isShutdown)
  }


  def sendDeathwatchNotifications(watchedBy: Ref[Set[ActorRef[Nothing]]], context: Context[Msg]): URIO[R, Unit] = {
    for {
      iAmWatchedBy <- watchedBy.get
      _ <- iAmWatchedBy.toList.traverse(actor =>
        ZIO.debug(s"${context.self} sending DeathWatchNotification to $actor") *> actor.asInstanceOf[ActorRef[Msg]].sendSystemMessage(DeathWatchNotification(context.self))
      )
    } yield ()
  }.foldM(_ => URIO.unit, either => URIO.effectTotal(either))

}
