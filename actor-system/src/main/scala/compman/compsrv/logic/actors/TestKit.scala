package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.{Queue, RIO, ZIO}
import zio.clock.Clock
import zio.duration.{durationInt, Duration}

import java.util.UUID

trait TestKit[F[+_]] {
  def ref: ActorRef[F]
  def expectMessage(timeout: Duration): RIO[Any with Clock, Option[F[Any]]]
}

object TestKit {
  def apply[F[+_]](actorSystem: ActorSystem): ZIO[Any with Clock, Throwable, TestKit[F]] =
    for {
      queue <- Queue.unbounded[F[Any]]
      actor <- actorSystem.make(
        UUID.randomUUID().toString,
        ActorConfig(),
        (),
        new ActorBehavior[Any, Unit, F] {
          override def receive[A](
            context: Context[F],
            actorConfig: ActorConfig,
            state: Unit,
            command: F[A],
            timers: Timers[Any, F]
          ): RIO[Any, (Unit, A)] = { queue.offer(command).as(((), ().asInstanceOf[A])) }
        }
      )
    } yield new TestKit[F] {
      override def ref: ActorRef[F] = actor

      override def expectMessage(timeout: Duration): RIO[Any with Clock, Option[F[Any]]] = queue.take.timeout(3.seconds)
    }
}
