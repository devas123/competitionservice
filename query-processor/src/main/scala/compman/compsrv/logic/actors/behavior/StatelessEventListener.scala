package compman.compsrv.logic.actors.behavior

import cats.implicits.catsSyntaxApplicativeError
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, MessageReceived, PublishMessage}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.model
import compman.compsrv.model.{Errors, Mapping}
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.command.Commands
import compman.compsrv.query.config.{MongodbConfig, StatelessEventListenerConfig}
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.repository._
import compman.compsrv.query.service.repository.AcademyOperations.AcademyService
import compservice.model.protobuf.callback.{CommandCallback, CommandExecutionResult}
import compservice.model.protobuf.event.Event
import org.mongodb.scala.MongoClient
import zio.{Cause, Tag, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

import java.util.UUID

object StatelessEventListener {
  sealed trait ApiCommand
  case class EventReceived(kafkaMessage: KafkaConsumerApi) extends ApiCommand
  case object Stop                                         extends ApiCommand

  trait StatelessEventListenerContext {
    implicit val eventMapping: Mapping.EventMapping[LIO]
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val academyService: AcademyService[LIO]
  }

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends StatelessEventListenerContext {
    implicit val eventMapping: Mapping.EventMapping[LIO] = model.Mapping.EventMapping.live
    implicit val loggingLive: CompetitionLogging.Service[LIO] = compman.compsrv.logic.logging.CompetitionLogging.Live
      .live[Any]
    override implicit val academyService: AcademyService[LIO] = AcademyOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  def behavior[R: Tag](
    config: StatelessEventListenerConfig,
    context: StatelessEventListenerContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand]
  ): ActorBehavior[R with Logging with Clock with Console, Unit, ApiCommand] = {

    import Behaviors._
    import context._
    import zio.interop.catz._

    Behaviors.behavior[R with Logging with Clock with Console, Unit, ApiCommand].withReceive {
      (context, _, state, command, _) =>
        {
          command match {
            case EventReceived(kafkaMessage) => kafkaMessage match {
                case KafkaSupervisor.QueryStarted()  => Logging.info("Kafka query started.").as(state)
                case KafkaSupervisor.QueryFinished() => Logging.info("Kafka query finished.").as(state)
                case KafkaSupervisor.QueryError(error) => Logging.error("Error during kafka query: ", Cause.fail(error))
                    .as(state)
                case MessageReceived(key, record) => {
                    for {
                      event  <- ZIO.effect(Event.parseFrom(record.value))
                      mapped <- EventMapping.mapEventDto[LIO](event)
                      _      <- Logging.info(s"Received event: $mapped")
                      result <- EventProcessors.applyStatelessEvent[LIO](mapped).attempt
                      message = result match {
                        case Left(value) => Commands.createErrorCallback(Commands.correlationId(event), Errors.InternalException(value))
                        case Right(_) => new CommandCallback().withId(UUID.randomUUID().toString)
                            .withCorrelationId(event.messageInfo.flatMap(_.correlationId).getOrElse(""))
                            .withResult(CommandExecutionResult.SUCCESS)
                      }
                      _ <- kafkaSupervisorActor ! PublishMessage(config.commandCallbackTopic, key, message.toByteArray)
                    } yield state
                  }.onError(cause => logError(cause.squash))
              }
            case Stop => Logging.info("Received stop command. Stopping...") *> context.stopSelf.as(state)
          }
        }
    }.withInit { (_, context, initState, _) =>
      for {
        adapter <- context.messageAdapter[KafkaConsumerApi](fa => Some(EventReceived(fa)))
        groupId = s"query-service-stateless-listener"
        _ <- kafkaSupervisorActor ! KafkaSupervisor.Subscribe(config.academyNotificationsTopic, groupId, adapter)
      } yield (Seq(), Seq.empty[ApiCommand], initState)
    }
  }
}
