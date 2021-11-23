package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.clock.Clock
import zio.duration.Duration
import zio.{IO, Queue, RIO, ZIO}

import java.util.UUID

trait TestKit[F[+_]] {
  def ref: ActorRef[F]

  def expectMessage(timeout: Duration): RIO[Any with Clock, Option[F[Any]]]

  def expectMessageClass[C](timeout: Duration, expectedMsgClass: Class[C]): RIO[Any with Clock, Option[C]]
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
                                 ): RIO[Any, (Unit, A)] = {
            queue.offer(command).as(((), ().asInstanceOf[A]))
          }
        }
      )
    } yield new TestKit[F] {
      override def ref: ActorRef[F] = actor

      override def expectMessage(timeout: Duration): RIO[Any with Clock, Option[F[Any]]] = queue.take.timeout(timeout)

      override def expectMessageClass[C](timeout: Duration, expectedMsgClass: Class[C]): RIO[Any with Clock, Option[C]] =
        queue
          .take
          .timeout(timeout)
          .flatMap(
            _.fold(
              RIO.fail(new RuntimeException(s"Received no message of type ${expectedMsgClass.getName} within $timeout")).as(Option.empty[C])
            )
            (msg => if (expectedMsgClass.isAssignableFrom(msg.getClass)) {
              RIO(Option(expectedMsgClass.cast(msg)))
            } else {
              RIO.fail(new RuntimeException(s"Expected class ${expectedMsgClass.getName} but received ${msg.getClass.getName}")).as(Option.empty[C])
            }
            ))
    }
}
