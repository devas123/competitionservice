package compman.compsrv.logic.actors.behavior

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.unsafe.IORuntime
import cats.effect.IO
import cats.implicits.catsSyntaxApplicative
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, MessageReceived, PublishMessage}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
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

object StatelessEventListener {
  sealed trait ApiCommand
  case class EventReceived(kafkaMessage: KafkaConsumerApi) extends ApiCommand
  case object Stop                                         extends ApiCommand

  trait StatelessEventListenerContext {
    implicit val eventMapping: Mapping.EventMapping[IO]
    implicit val academyService: AcademyService[IO]
    implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
  }

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends StatelessEventListenerContext {
    implicit val eventMapping: Mapping.EventMapping[IO] = model.Mapping.EventMapping.live
    override implicit val academyService: AcademyService[IO] = AcademyOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  def behavior(
    config: StatelessEventListenerConfig,
    context: StatelessEventListenerContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand]
  ): Behavior[ApiCommand] = {

    import context._

    Behaviors.setup { context =>
      val adapter = context.messageAdapter[KafkaConsumerApi](fa => EventReceived(fa))
      val groupId = s"query-service-stateless-listener"
      kafkaSupervisorActor !
        KafkaSupervisor
          .QueryAndSubscribe(config.academyNotificationsTopic, groupId, adapter, commitOffsetToKafka = true)

      Behaviors.receiveMessage {
        case EventReceived(kafkaMessage) => kafkaMessage match {
            case KafkaSupervisor.QueryStarted() =>
              context.log.info("Kafka query started.")
              Behaviors.same

            case KafkaSupervisor.QueryFinished(_) =>
              context.log.info("Kafka query finished.")
              Behaviors.same

            case KafkaSupervisor.QueryError(error) =>
              context.log.error("Error during kafka query: ", error)
              Behaviors.same

            case MessageReceived(key, record) =>
              val event = Event.parseFrom(record.value)
              (for {
                mapped <- EventMapping.mapEventDto[IO](event)
                _      <- IO(context.log.info(s"Received event: $mapped"))
                result <- EventProcessors.applyStatelessEvent[IO](mapped).attempt
                message = result match {
                  case Left(value) => Commands
                      .createErrorCallback(Commands.correlationId(event), Errors.InternalException(value))
                  case Right(_) => new CommandCallback()
                      .withCorrelationId(event.messageInfo.flatMap(_.correlationId).get)
                      .withResult(CommandExecutionResult.SUCCESS).withNumberOfEvents(event.numberOfEventsInBatch)
                }
                _ <- IO(kafkaSupervisorActor ! PublishMessage(config.commandCallbackTopic, key, message.toByteArray))
                  .whenA(
                    event.numberOfEventsInBatch - 1 == event.localEventNumber ||
                      message.result != CommandExecutionResult.SUCCESS
                  )
              } yield ()).onError(cause => IO(context.log.error("Error in stateless event listener.", cause)))
                .unsafeRunSync()
              Behaviors.same
        }
        case Stop => Behaviors.stopped(() => context.log.info("Received stop command. Stopping..."))
      }
    }
  }
}
