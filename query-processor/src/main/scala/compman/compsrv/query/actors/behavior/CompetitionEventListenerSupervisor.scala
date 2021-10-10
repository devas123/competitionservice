package compman.compsrv.query.actors.behavior

import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.model.{CommandProcessorNotification, CompetitionProcessingStarted, CompetitionProcessingStopped}
import compman.compsrv.query.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import io.getquill.CassandraZioSession
import zio.{Fiber, Ref, RIO, Tag, Task, ZIO}
import zio.clock.Clock
import zio.kafka.consumer.Consumer
import zio.logging.Logging

import java.io.{PrintWriter, StringWriter}

object CompetitionEventListenerSupervisor {
  type SupervisorEnvironment[R] = Clock with R with Logging
  sealed trait ActorMessages[+_]
  case class ReceivedNotification(notification: CommandProcessorNotification) extends ActorMessages[Unit]
  case class ActiveCompetition(managedCompetition: ManagedCompetition)        extends ActorMessages[Unit]

  trait ActorContext {
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val managedCompetitionsOperations: ManagedCompetitionService[LIO]
  }

  case class Live(cassandraZioSession: CassandraZioSession) extends ActorContext {
    override implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO] = compman.compsrv
      .logic.logging.CompetitionLogging.Live.live[Any]
    override implicit val managedCompetitionsOperations: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .live(cassandraZioSession)
  }

  case class Test(competitions: Ref[Map[String, ManagedCompetition]]) extends ActorContext {
    override implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO] = compman.compsrv
      .logic.logging.CompetitionLogging.Live.live[Any]
    override implicit val managedCompetitionsOperations: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .test(competitions)
  }

  def behavior[R: Tag](
    eventStreaming: EventStreaming[R],
    notificationStopic: String,
    context: ActorContext,
    eventListenerContext: CompetitionEventListener.ActorContext,
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): ActorBehavior[SupervisorEnvironment[R], Unit, ActorMessages] =
    new ActorBehavior[SupervisorEnvironment[R], Unit, ActorMessages] {
      import context._
      override def receive[A](
        context: Context[ActorMessages],
        actorConfig: ActorConfig,
        state: Unit,
        command: ActorMessages[A],
        timers: Timers[SupervisorEnvironment[R], ActorMessages]
      ): RIO[SupervisorEnvironment[R], (Unit, A)] = {
        command match {
          case ActiveCompetition(competition) => for {
              _ <- Logging.info(s"Creating listener actor for competition $competition")
              res <- context
                .make[
                  R with Logging with Clock,
                  CompetitionEventListener.ActorState,
                  CompetitionEventListener.ApiCommand
                ](
                  competition.id,
                  ActorConfig(),
                  CompetitionEventListener.initialState,
                  CompetitionEventListener.behavior[R](
                    competition.id,
                    eventStreaming,
                    competition.eventsTopic,
                    eventListenerContext,
                    websocketConnectionSupervisor
                  )
                ).map(_ => ((), ().asInstanceOf[A]))
              _ <- Logging.info(s"Created actor to process the competition ${competition.id}")
            } yield res
          case ReceivedNotification(notification) => notification match {
              case CompetitionProcessingStarted(id, topic, creatorId, createdAt, startsAt, endsAt, timeZone, status) =>
                for {
                  _ <- Logging.info(s"Processing competition processing started notification $id")
                  _ <- ManagedCompetitionsOperations.addManagedCompetition[LIO](
                    ManagedCompetition(id, topic, creatorId, createdAt, startsAt, Option(endsAt), timeZone, status)
                  ).onError(err =>
                    Logging.error(s"Error while saving. ${err.failures.map(t => {
                      val writer = new StringWriter()
                      t.printStackTrace(new PrintWriter(writer))
                      writer.toString
                    }).mkString("\n")}")
                  )
                  _ <- Logging.info(s"Added competition $id to db.")
                  res <- context
                    .make[
                      R with Logging with Clock,
                      CompetitionEventListener.ActorState,
                      CompetitionEventListener.ApiCommand
                    ](
                      id,
                      ActorConfig(),
                      CompetitionEventListener.initialState,
                      CompetitionEventListener
                        .behavior[R](id, eventStreaming, topic, eventListenerContext, websocketConnectionSupervisor)
                    ).foldM(
                      _ => Logging.info(s"Actor already exists with id $id"),
                      _ => Logging.info(s"Created actor to process the competition $id")
                    ).map(_ => ((), ().asInstanceOf[A]))
                } yield res // start new actor if not started
              case CompetitionProcessingStopped(id) => for {
                  _     <- ManagedCompetitionsOperations.deleteManagedCompetition[LIO](id)
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
          activeCompetitions <- ManagedCompetitionsOperations.getActiveCompetitions[LIO].foldM(
            err => logError(err) *> ZIO.effectTotal(List.empty),
            competitions =>
              Logging.info(s"Found following competitions: $competitions") *> ZIO.effectTotal(competitions)
          )
          events <- ZIO.effect { activeCompetitions.map(ActiveCompetition) }
          k <- (for {
            _ <- Logging.info(s"Starting stream for listening to global notifications: $notificationStopic")
            _ <- eventStreaming.getByteArrayStream(notificationStopic, s"query-service-global-events-listener")
              .mapM(record =>
                (for {
                  notif <- ZIO.effect(mapper.readValue(record.value, classOf[CommandProcessorNotification]))
                    .onError(err => Logging.error(s"Error while deserialize: $err", err))
                  _ <- Logging.info(s"Received notification $notif")
                  _ <- context.self ! ReceivedNotification(notif)
                } yield ()).as(record.offset)
              ).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain
            _ <- Logging.info(s"Finished stream for listening to global notifications: $notificationStopic")
          } yield ()).fork
        } yield (Seq(k), events)
      }
    }
}
