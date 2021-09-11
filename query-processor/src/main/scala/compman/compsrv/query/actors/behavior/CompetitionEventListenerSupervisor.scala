package compman.compsrv.query.actors.behavior

import compman.compsrv.model.{CommandProcessorNotification, CompetitionProcessingStarted, CompetitionProcessingStopped}
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import compman.compsrv.query.service.repository.ManagedCompetitions
import zio.{Fiber, RIO, Task, ZIO}
import zio.clock.Clock

object CompetitionEventListenerSupervisor {
  sealed trait ActorMessages[+_]
  case class ReceivedNotification(notification: CommandProcessorNotification) extends ActorMessages[Unit]
  def behavior[R](
    eventStreaming: EventStreaming[R]
  ): ActorBehavior[ManagedCompetitions.Service with Clock with R, Unit, ActorMessages] =
    new ActorBehavior[ManagedCompetitions.Service with Clock with R, Unit, ActorMessages] {
      override def receive[A](
        context: Context[ActorMessages],
        actorConfig: ActorConfig,
        state: Unit,
        command: ActorMessages[A],
        timers: Timers[ManagedCompetitions.Service with Clock with R, ActorMessages]
      ): RIO[ManagedCompetitions.Service with Clock with R, (Unit, A)] = {
        command match {
          case ReceivedNotification(notification) => notification match {
              case CompetitionProcessingStarted(id, topic) => context
                  .make[R, CompetitionApiActor.ActorState, CompetitionApiActor.ApiCommand](
                    id,
                    ActorConfig(),
                    CompetitionApiActor.initialState,
                    CompetitionApiActor.behavior[R](eventStreaming, topic)
                  ).map(_ => ((), ().asInstanceOf[A])) // start new actor if not started
              case CompetitionProcessingStopped(id) => for {
                  child <- context.findChild[Any](id)
                  _ <- child match {
                    case Some(value) => value.stop.ignore
                    case None        => Task.unit
                  }
                } yield ((), ().asInstanceOf[A])
            }
        }
      }

      override def init(
        actorConfig: ActorConfig,
        context: Context[ActorMessages],
        initState: Unit,
        timers: Timers[ManagedCompetitions.Service with Clock with R, ActorMessages]
      ): RIO[
        ManagedCompetitions.Service with Clock with R,
        (Seq[Fiber.Runtime[Throwable, Unit]], Seq[ActorMessages[Any]])
      ] = {
        for {
          mapper <- ZIO.effect(ObjectMapperFactory.createObjectMapper)
          k <- eventStreaming.getByteArrayStream("").mapM(record =>
            for {
              notif <- ZIO.effect(mapper.readValue(record, classOf[CommandProcessorNotification]))
              _     <- context.self ! ReceivedNotification(notif)
            } yield ()
          ).runDrain.fork
        } yield (Seq(k), Seq.empty[ActorMessages[Any]])
      }
    }
}
