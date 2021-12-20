package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.Watch
import zio.{Queue, RIO, Task, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration

import java.util.UUID

trait TestKit[F] {
  def ref: ActorRef[F]

  def expectMessage(timeout: Duration): RIO[Any with Clock, Option[F]]
  def watchWith[F1](msg: F, actorRef: ActorRef[F1]): Task[Unit]

  def expectMessageClass[C](timeout: Duration, expectedMsgClass: Class[C]): RIO[Any with Clock, Option[C]]
  def expectOneOf(timeout: Duration, expectedMsgClass: Class[_]*): RIO[Any with Clock, Option[Any]]
}

object TestKit {
  import Behaviors._
  def apply[F](actorSystem: ActorSystem): ZIO[Any with Clock with Console, Throwable, TestKit[F]] =
    for {
      queue <- Queue.unbounded[F]
      actor <- actorSystem.make(
        s"TestKit-${UUID.randomUUID()}",
        ActorConfig(),
        (),
        Behaviors.behavior[Any, Unit, F]
          .withReceive { (_: Context[F], _: ActorConfig, _: Unit, command: F, _: Timers[Any, F]) =>
            { for { _ <- queue.offer(command) } yield () }
          }
          .withPostStop((_, _, _, _) => queue.shutdown)
      )
    } yield new TestKit[F] {
      override def ref: ActorRef[F] = actor

      override def expectMessage(timeout: Duration): RIO[Any with Clock, Option[F]] = queue.take.timeout(timeout)

      override def expectMessageClass[C](
        timeout: Duration,
        expectedMsgClass: Class[C]
      ): RIO[Any with Clock, Option[C]] = {
        for {
          nextMsg <- queue.take.timeout(timeout)
          msg <-
            if (nextMsg.exists(n => expectedMsgClass.isAssignableFrom(n.getClass))) {
              RIO(nextMsg.map(n => expectedMsgClass.cast(n)))
            } else {
              RIO.fail(new RuntimeException(
                s"Expected class Some(${expectedMsgClass.getName}), but received ${nextMsg.map(_.getClass.getName)}"
              ))
            }
        } yield msg
      }

      override def watchWith[F1](msg: F, actorRef: ActorRef[F1]): Task[Unit] = {
        actor sendSystemMessage Watch(actorRef, actor, Option(msg))
      }

      override def expectOneOf(timeout: Duration, expectedMsgClass: Class[_]*): RIO[Any with Clock, Option[Any]] =         for {
        nextMsg <- queue.take.timeout(timeout)
        msg <-
          expectedMsgClass.find(c => nextMsg.exists(n => c.isAssignableFrom(n.getClass))) match {
            case Some(value) => RIO(nextMsg.map(n => value.cast(n)))
            case None => RIO.fail(new RuntimeException(
              s"Expected class Some(${expectedMsgClass.map(_.getName).mkString(", ")}), but received ${nextMsg.map(_.getClass.getName)}"
            ))
          }
      } yield msg

    }
}
