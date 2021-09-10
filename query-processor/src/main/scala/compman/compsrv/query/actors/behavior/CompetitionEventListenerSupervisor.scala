package compman.compsrv.query.actors.behavior

import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.service.repository.ManagedCompetitions
import zio.{Fiber, RIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.serde.Serde

object CompetitionEventListenerSupervisor {
  sealed trait ActorMessages[+_]
  case class DefaultMsg() extends ActorMessages[Unit]
  case class Test(bytes: Array[Byte]) extends ActorMessages[Unit]
  val behavior: ActorBehavior[ManagedCompetitions.Service with Clock with Blocking with Consumer, Unit, ActorMessages] =
    new ActorBehavior[ManagedCompetitions.Service with Clock with Blocking with Consumer, Unit, ActorMessages] {
      override def receive[A](
        context: Context[ActorMessages],
        actorConfig: ActorConfig,
        state: Unit,
        command: ActorMessages[A],
        timers: Timers[ManagedCompetitions.Service with Clock with Blocking with Consumer, ActorMessages]
      ): RIO[ManagedCompetitions.Service with Clock with Blocking with Consumer, (Unit, A)] = ???

      override def init(
        actorConfig: ActorConfig,
        context: Context[ActorMessages],
        initState: Unit,
        timers: Timers[ManagedCompetitions.Service with Clock with Blocking with Consumer, ActorMessages]
      ): RIO[ManagedCompetitions.Service with Clock with Blocking with Consumer, (Seq[Fiber.Runtime[Throwable, Unit]], Seq[ActorMessages[Any]])] = {
        for {k <- consumer.Consumer.subscribeAnd(Subscription.topics(""))
          .plainStream(Serde.string, Serde.byteArray)
          .mapM(record => context.self ! Test(record.value))
          .runDrain
          .fork
             } yield (Seq(k), Seq.empty[ActorMessages[Any]])
      }
    }
}
