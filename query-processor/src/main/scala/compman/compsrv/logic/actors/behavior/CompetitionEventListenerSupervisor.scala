package compman.compsrv.logic.actors.behavior

import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, QueryAndSubscribe}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.CompetitionEventListener.Stop
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.model.mapping.DtoMapping.toInstant
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import compservice.model.protobuf.eventpayload.CompetitionPropertiesUpdatedPayload
import compservice.model.protobuf.model.CompetitionProcessorNotification
import org.mongodb.scala.MongoClient
import zio.{Ref, Tag, Task, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

import java.io.{PrintWriter, StringWriter}

object CompetitionEventListenerSupervisor {
  type SupervisorEnvironment[R] = Clock with R with Logging with Console
  sealed trait ActorMessages
  case class ReceivedNotification(notification: CompetitionProcessorNotification.Notification)   extends ActorMessages
  case class ActiveCompetition(managedCompetition: ManagedCompetition)                           extends ActorMessages
  case class CompetitionUpdated(update: CompetitionPropertiesUpdatedPayload, eventTopic: String) extends ActorMessages
  case class CompetitionDeletedMessage(competitionId: String)                                    extends ActorMessages
  case class KafkaNotification(msg: String)                                                      extends ActorMessages

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
    callbackTopic: String,
    context: ActorContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    eventListenerContext: CompetitionEventListener.ActorContext,
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): ActorBehavior[SupervisorEnvironment[R], Unit, ActorMessages] = {
    import compman.compsrv.logic.actors.Behaviors
    import context._
    import Behaviors._
    Behaviors.behavior[SupervisorEnvironment[R], Unit, ActorMessages].withReceive { (context, _, _, command, _) =>
      {
        command match {
          case KafkaNotification(msg) => Logging.info(msg).unit
          case CompetitionDeletedMessage(competitionId) => ManagedCompetitionsOperations
              .deleteManagedCompetition[LIO](competitionId).unit
          case CompetitionUpdated(update, eventTopic) => for {
            _ <- Logging.info(s"Competition properties updated $update")
            props <- ZIO.effect(update.getProperties)
              _ <- ManagedCompetitionsOperations.updateManagedCompetition[LIO](ManagedCompetition(
                props.id,
                Option(props.competitionName),
                eventTopic,
                Option(props.creatorId),
                toInstant(props.getCreationTimestamp),
                toInstant(props.getStartDate),
                Option(props.getEndDate).map(toInstant),
                props.timeZone,
                props.status
              ))
            } yield ()
          case ActiveCompetition(competition) => for {
              res <- context
                .make[
                  R with Logging with Clock with Console,
                  CompetitionEventListener.ActorState,
                  CompetitionEventListener.ApiCommand
                ](
                  competition.id,
                  ActorConfig(),
                  CompetitionEventListener.initialState,
                  CompetitionEventListener.behavior[R](
                    competition.id,
                    competition.eventsTopic,
                    callbackTopic,
                    eventListenerContext,
                    kafkaSupervisorActor,
                    context.self,
                    websocketConnectionSupervisor
                  )
                ).foldM(
                  _ => Logging.debug(s"Actor already exists with id ${competition.id}"),
                  _ => Logging.info(s"Created actor to process the competition ${competition.id}")
                ).unit
            } yield res
          case ReceivedNotification(notification) => notification.started.map { s =>
              for {
                _ <- Logging.info(s"Processing competition processing started notification ${s.id}")
                _ <- ManagedCompetitionsOperations.addManagedCompetition[LIO](ManagedCompetition(
                  s.id,
                  Option(s.name),
                  s.topic,
                  Option(s.creatorId),
                  s.createdAt.map(toInstant).get,
                  s.startsAt.map(toInstant).get,
                  s.endsAt.map(toInstant),
                  s.timeZone,
                  s.status
                )).onError(err =>
                  Logging.error(s"Error while saving. ${err.failures.map(t => {
                    val writer = new StringWriter()
                    t.printStackTrace(new PrintWriter(writer))
                    writer.toString
                  }).mkString("\n")}")
                )
                _ <- Logging.info(s"Added competition ${s.id} to db.")
                res <- context
                  .make[
                    R with Logging with Clock with Console,
                    CompetitionEventListener.ActorState,
                    CompetitionEventListener.ApiCommand
                  ](
                    s.id,
                    ActorConfig(),
                    CompetitionEventListener.initialState,
                    CompetitionEventListener.behavior[R](
                      s.id,
                      s.topic,
                      callbackTopic,
                      eventListenerContext,
                      kafkaSupervisorActor,
                      context.self,
                      websocketConnectionSupervisor
                    )
                  ).foldM(
                    _ => Logging.info(s"Actor already exists with id ${s.id}"),
                    _ => Logging.info(s"Created actor to process the competition ${s.id}")
                  ).unit
              } yield res
            }.orElse(notification.stopped.map { s =>
              for {
                _ <- Logging
                  .info(s"Stopping competition listener with id ${s.id} because competition processing stopped.")
                child <- context.findChild[CompetitionEventListener.ApiCommand](s.id)
                _ <- child match {
                  case Some(value) => value ! Stop
                  case None        => Task.unit
                }
              } yield ()
            }).get
        }
      }.onError(cause => logError(cause.squashTrace))
    }.withInit { (_, context, _, _) =>
      {
        for {
          adapter <- context.messageAdapter[KafkaConsumerApi] {
            case KafkaSupervisor.QueryStarted()    => Some(KafkaNotification("Query started."))
            case KafkaSupervisor.QueryFinished()   => Some(KafkaNotification("Query finished."))
            case KafkaSupervisor.QueryError(error) => Some(KafkaNotification(s"Query error. $error"))
            case KafkaSupervisor.MessageReceived(_, record) =>
              val notif = CompetitionProcessorNotification.parseFrom(record.value)
              Some(ReceivedNotification(notif.notification))
          }
          activeCompetitions <- ManagedCompetitionsOperations.getActiveCompetitions[LIO].foldM(
            err => logError(err) *> ZIO.effectTotal(List.empty),
            competitions =>
              Logging.info(s"Found following competitions: $competitions") *> ZIO.effectTotal(competitions)
          )
          events <- ZIO.effect { activeCompetitions.map(ActiveCompetition) }
          _ <- kafkaSupervisorActor !
            QueryAndSubscribe(notificationStopic, s"query-service-global-events-listener", adapter)
        } yield (Seq(), events, ())
      }
    }
  }
}
