package compman.compsrv.logic.actors.behavior

import cats.arrow.FunctionK
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, QueryAndSubscribe}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.CompetitionEventListener.Stop
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.model.{CommandProcessorNotification, CompetitionProcessingStarted, CompetitionProcessingStopped}
import compman.compsrv.model.events.payload.CompetitionPropertiesUpdatedPayload
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.serde.ObjectMapperFactory
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import org.mongodb.scala.MongoClient
import zio.{Fiber, Ref, RIO, Tag, Task, ZIO}
import zio.clock.Clock
import zio.logging.Logging

import java.io.{PrintWriter, StringWriter}

object CompetitionEventListenerSupervisor {
  type SupervisorEnvironment[R] = Clock with R with Logging
  sealed trait ActorMessages[+_]
  case class ReceivedNotification(notification: CommandProcessorNotification) extends ActorMessages[Unit]
  case class ActiveCompetition(managedCompetition: ManagedCompetition)        extends ActorMessages[Unit]
  case class CompetitionUpdated(update: CompetitionPropertiesUpdatedPayload, eventTopic: String)
      extends ActorMessages[Unit]
  case class KafkaNotification(msg: String) extends ActorMessages[Unit]

  trait ActorContext {
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val managedCompetitionsOperations: ManagedCompetitionService[LIO]
  }

  case class Live(mongoclient: MongoClient, mongoConfig: MongodbConfig) extends ActorContext {
    override implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO] = compman.compsrv
      .logic.logging.CompetitionLogging.Live.live[Any]
    override implicit val managedCompetitionsOperations: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .live(mongoclient, mongoConfig.queryDatabaseName)
  }

  case class Test(competitions: Ref[Map[String, ManagedCompetition]]) extends ActorContext {
    override implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO] = compman.compsrv
      .logic.logging.CompetitionLogging.Live.live[Any]
    override implicit val managedCompetitionsOperations: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .test(competitions)
  }

  def behavior[R: Tag](
    notificationStopic: String,
    context: ActorContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
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
          case KafkaNotification(msg) => Logging.info(msg).as(((), ().asInstanceOf[A]))
          case CompetitionUpdated(update, eventTopic) => for {
              props <- ZIO.effect(update.getProperties)
              _ <- ManagedCompetitionsOperations.updateManagedCompetition[LIO](ManagedCompetition(
                props.getId,
                props.getCompetitionName,
                eventTopic,
                props.getCreatorId,
                props.getCreationTimestamp,
                props.getStartDate,
                Option(props.getEndDate),
                props.getTimeZone,
                props.getStatus
              ))
              _ <- Logging.info(s"Competition properties updated $update")
            } yield ((), ().asInstanceOf[A])
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
                    ObjectMapperFactory.createObjectMapper,
                    competition.id,
                    competition.eventsTopic,
                    eventListenerContext,
                    kafkaSupervisorActor,
                    context.self,
                    websocketConnectionSupervisor
                  )
                ).foldM(
                  _ => Logging.info(s"Actor already exists with id ${competition.id}"),
                  _ => Logging.info(s"Created actor to process the competition ${competition.id}")
                ).map(_ => ((), ().asInstanceOf[A]))
              _ <- Logging.info(s"Created actor to process the competition ${competition.id}")
            } yield res
          case ReceivedNotification(notification) => notification match {
              case CompetitionProcessingStarted(
                    id,
                    name,
                    topic,
                    creatorId,
                    createdAt,
                    startsAt,
                    endsAt,
                    timeZone,
                    status
                  ) => for {
                  _ <- Logging.info(s"Processing competition processing started notification $id")
                  _ <- ManagedCompetitionsOperations.addManagedCompetition[LIO](ManagedCompetition(
                    id,
                    name,
                    topic,
                    creatorId,
                    createdAt,
                    startsAt,
                    Option(endsAt),
                    timeZone,
                    status
                  )).onError(err =>
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
                      CompetitionEventListener.behavior[R](
                        ObjectMapperFactory.createObjectMapper,
                        id,
                        topic,
                        eventListenerContext,
                        kafkaSupervisorActor,
                        context.self,
                        websocketConnectionSupervisor
                      )
                    ).foldM(
                      _ => Logging.info(s"Actor already exists with id $id"),
                      _ => Logging.info(s"Created actor to process the competition $id")
                    ).map(_ => ((), ().asInstanceOf[A]))
                } yield res // start new actor if not started
              case CompetitionProcessingStopped(id) => for {
                  child <- context.findChild[Any](id)
                  _ <- child match {
                    case Some(value) => value ! Stop
                    case None        => Task.unit
                  }
                } yield ((), ().asInstanceOf[A])
            }
        }
      }.onError(cause => logError(cause.squashTrace))

      override def init(
        actorConfig: ActorConfig,
        context: Context[ActorMessages],
        initState: Unit,
        timers: Timers[SupervisorEnvironment[R], ActorMessages]
      ): RIO[SupervisorEnvironment[R], (Seq[Fiber.Runtime[Throwable, Unit]], Seq[ActorMessages[Any]])] = {
        for {
          mapper <- ZIO.effect(ObjectMapperFactory.createObjectMapper)
          adapter <- context.messageAdapter(new FunctionK[KafkaConsumerApi, ActorMessages] {
            override def apply[A](fa: KafkaConsumerApi[A]): ActorMessages[A] = {
              fa match {
                case KafkaSupervisor.QueryStarted()    => KafkaNotification("Query started.")
                case KafkaSupervisor.QueryFinished()   => KafkaNotification("Query finished.")
                case KafkaSupervisor.QueryError(error) => KafkaNotification(s"Query error. $error")
                case KafkaSupervisor.MessageReceived(_, record) =>
                  val notif = mapper.readValue(record.value, classOf[CommandProcessorNotification])
                  ReceivedNotification(notif)
              }
            }.asInstanceOf[ActorMessages[A]]
          })
          activeCompetitions <- ManagedCompetitionsOperations.getActiveCompetitions[LIO].foldM(
            err => logError(err) *> ZIO.effectTotal(List.empty),
            competitions =>
              Logging.info(s"Found following competitions: $competitions") *> ZIO.effectTotal(competitions)
          )
          events <- ZIO.effect { activeCompetitions.map(ActiveCompetition) }
          _ <- kafkaSupervisorActor !
            QueryAndSubscribe(notificationStopic, s"query-service-global-events-listener", adapter)
        } yield (Seq(), events)
      }
    }
}
