package compman.compsrv.query.actors.behavior

import compman.compsrv.model.{CommandProcessorNotification, CompetitionProcessingStarted, CompetitionProcessingStopped}
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import compman.compsrv.query.service.repository.{ManagedCompetitionsOperations, RepoEnvironment}
import io.getquill.CassandraZioSession
import zio.{Fiber, RIO, Tag, Task, ZIO}
import zio.clock.Clock
import zio.logging.Logging

object CompetitionEventListenerSupervisor {
  type SupervisorEnvironment[R] = ManagedCompetitionsOperations.Service[R] with Clock with R with Logging
  sealed trait ActorMessages[+_]
  case class ReceivedNotification(notification: CommandProcessorNotification) extends ActorMessages[Unit]
  def behavior[R: Tag](
    eventStreaming: EventStreaming[R],
    cassandraZioSession: CassandraZioSession
  ): ActorBehavior[SupervisorEnvironment[R], Unit, ActorMessages] =
    new ActorBehavior[SupervisorEnvironment[R], Unit, ActorMessages] {
      override def receive[A](
        context: Context[ActorMessages],
        actorConfig: ActorConfig,
        state: Unit,
        command: ActorMessages[A],
        timers: Timers[SupervisorEnvironment[R], ActorMessages]
      ): RIO[SupervisorEnvironment[R], (Unit, A)] = {
        command match {
          case ReceivedNotification(notification) => notification match {
              case CompetitionProcessingStarted(id, topic, creatorId, createdAt, startsAt, endsAt, timeZone, status) =>
                for {
                  _ <- ManagedCompetitionsOperations.addManagedCompetition[R](
                    ManagedCompetition(id, topic, creatorId, createdAt, startsAt, endsAt, timeZone, status)
                  )
                  res <- context.make[R with Logging, CompetitionEventListener.ActorState, CompetitionEventListener.ApiCommand](
                    id,
                    ActorConfig(),
                    CompetitionEventListener.initialState,
                    CompetitionEventListener.behavior[R](eventStreaming, topic, CompetitionEventListener.Live(cassandraZioSession))
                  ).map(_ => ((), ().asInstanceOf[A]))
                } yield res // start new actor if not started
              case CompetitionProcessingStopped(id) => for {
                  _     <- ManagedCompetitionsOperations.deleteManagedCompetition[R](id)
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
        timers: Timers[SupervisorEnvironment[R], ActorMessages]
      ): RIO[SupervisorEnvironment[R], (Seq[Fiber.Runtime[Throwable, Unit]], Seq[ActorMessages[Any]])] = {
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
