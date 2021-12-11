package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.{Queue, RIO, ZIO}
import zio.clock.Clock
import zio.duration.Duration

import java.util.UUID

trait TestKit[F] {
  def ref: ActorRef[F]

  def expectMessage(timeout: Duration): RIO[Any with Clock, Option[F]]

  def expectMessageClass[C](timeout: Duration, expectedMsgClass: Class[C]): RIO[Any with Clock, Option[C]]
}

object TestKit {
  def apply[F](actorSystem: ActorSystem): ZIO[Any with Clock, Throwable, TestKit[F]] =
    for {
      queue <- Queue.unbounded[F]
      actor <- actorSystem.make(
        UUID.randomUUID().toString,
        ActorConfig(),
        (),
        new ActorBehavior[Any, Unit, F] {
          override def receive(
            context: Context[F],
            actorConfig: ActorConfig,
            state: Unit,
            command: F,
            timers: Timers[Any, F]
          ): RIO[Any, Unit] = { for { _ <- queue.offer(command) } yield () }
        }
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
                s"Expected class ${expectedMsgClass.getName} but received ${nextMsg.map(_.getClass.getName)}"
              ))
            }
        } yield msg
      }
    }
}
