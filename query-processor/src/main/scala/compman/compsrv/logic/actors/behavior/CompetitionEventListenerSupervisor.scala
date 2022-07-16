package compman.compsrv.logic.actors.behavior

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, QueryAndSubscribe}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors.behavior.CompetitionEventListener.Stop
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.model.mapping.DtoMapping.toInstant
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import compservice.model.protobuf.eventpayload.CompetitionPropertiesUpdatedPayload
import compservice.model.protobuf.model.CompetitionProcessorNotification
import org.mongodb.scala.MongoClient

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object CompetitionEventListenerSupervisor {
  sealed trait ActorMessages
  case class ReceivedNotification(notification: CompetitionProcessorNotification.Notification)   extends ActorMessages
  case class ActiveCompetitions(managedCompetitions: List[ManagedCompetition])                   extends ActorMessages
  case class CompetitionUpdated(update: CompetitionPropertiesUpdatedPayload, eventTopic: String) extends ActorMessages
  case class CompetitionDeletedMessage(competitionId: String)                                    extends ActorMessages
  case class KafkaNotification(msg: String)                                                      extends ActorMessages
  case class CompetitionEventListenerStopped(id: String)                                         extends ActorMessages

  trait ActorContext  extends WithIORuntime {
    implicit val managedCompetitionsOperations: ManagedCompetitionService[IO]
  }

  case class Live(mongoclient: MongoClient, mongoConfig: MongodbConfig) extends ActorContext {
    override implicit val managedCompetitionsOperations: ManagedCompetitionService[IO] = ManagedCompetitionsOperations
      .live(mongoclient, mongoConfig.queryDatabaseName)
  }

  case class Test(competitions: AtomicReference[Map[String, ManagedCompetition]]) extends ActorContext {
    override implicit val managedCompetitionsOperations: ManagedCompetitionService[IO] = ManagedCompetitionsOperations
      .test(competitions)
  }

  def behavior(
    notificationStopic: String,
    callbackTopic: String,
    actorContext: ActorContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    eventListenerContext: CompetitionEventListener.ActorContext,
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): Behavior[ActorMessages] = {
    import actorContext._
    val competitionListeners = mutable.HashMap.empty[String, ActorRef[CompetitionEventListener.ApiCommand]]
    Behaviors.setup { context =>
      def createCompetitionProcessingActorIfMissing(id: String, eventsTopic: String): Unit = {
        if (competitionListeners.contains(id)) {
          val actor = context.spawn(
            Behaviors.supervise(CompetitionEventListener.behavior(
              id,
              eventsTopic,
              callbackTopic,
              eventListenerContext,
              kafkaSupervisorActor,
              context.self,
              websocketConnectionSupervisor
            )).onFailure(SupervisorStrategy.restart.withLimit(100, 1.minute)),
            id
          )
          context.watchWith(actor, CompetitionEventListenerStopped(id))
          competitionListeners.put(id, actor)
          context.log.info(s"Created actor to process the competition $id")
        } else { context.log.debug(s"Actor already exists with id $id") }
      }

      val adapter = context.messageAdapter[KafkaConsumerApi] {
        case KafkaSupervisor.QueryStarted()    => KafkaNotification("Query started.")
        case KafkaSupervisor.QueryFinished(_)  => KafkaNotification("Query finished.")
        case KafkaSupervisor.QueryError(error) => KafkaNotification(s"Query error. $error")
        case KafkaSupervisor.MessageReceived(_, record) =>
          val notif = CompetitionProcessorNotification.parseFrom(record.value)
          ReceivedNotification(notif.notification)
      }
      kafkaSupervisorActor ! QueryAndSubscribe(notificationStopic, s"query-service-global-events-listener", adapter)
      context.pipeToSelf((for {
        activeCompetitions <- ManagedCompetitionsOperations.getActiveCompetitions[IO]
      } yield activeCompetitions).unsafeToFuture()) {
        case Failure(exception) =>
          ActiveCompetitions(List.empty)
        case Success(value) => ActiveCompetitions(value)
      }

      Behaviors.receiveMessage {
        case CompetitionEventListenerStopped(id) =>
          competitionListeners.remove(id)
          Behaviors.same
        case KafkaNotification(msg) =>
          context.log.info(msg)
          Behaviors.same
        case CompetitionDeletedMessage(competitionId) =>
          ManagedCompetitionsOperations.deleteManagedCompetition[IO](competitionId).unsafeRunSync()
          Behaviors.same
        case CompetitionUpdated(update, eventTopic) =>
          context.log.info(s"Competition properties updated $update")
          val props = update.getProperties
          (for {
            _ <- ManagedCompetitionsOperations.updateManagedCompetition[IO](ManagedCompetition(
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
          } yield ()).unsafeRunSync()
          Behaviors.same
        case ActiveCompetitions(competitions) =>
          competitions.foreach { competition =>
            createCompetitionProcessingActorIfMissing(competition.id, competition.eventsTopic)
          }
          Behaviors.same
        case ReceivedNotification(notification) =>
          if (notification.isStarted) {
            val s = notification.started.get
            (for {
              _ <- IO(context.log.info(s"Processing competition processing started notification ${s.id}"))
              _ <- ManagedCompetitionsOperations.addManagedCompetition[IO](ManagedCompetition(
                s.id,
                Option(s.name),
                s.topic,
                Option(s.creatorId),
                s.createdAt.map(toInstant).get,
                s.startsAt.map(toInstant).get,
                s.endsAt.map(toInstant),
                s.timeZone,
                s.status
              )).onError(err => IO(context.log.error(s"Error while saving.", err)))
              _   <- IO(context.log.info(s"Added competition ${s.id} to db."))
              res <- IO(createCompetitionProcessingActorIfMissing(s.id, s.topic))
            } yield res).unsafeRunSync()
          }
          if (notification.isStopped) {
            val s = notification.stopped.get
            context.log.info(s"Stopping competition listener with id ${s.id} because competition processing stopped.")
            competitionListeners.get(s.id).foreach(actor => actor ! Stop("Competition processing stopped."))
          }
          Behaviors.same
      }
    }
  }
}
