package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.SerdeApi.commandDeserializer
import compman.compsrv.logic.Operations._
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.LiveEnv
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging.{LIO, logError}
import compman.compsrv.logic.logging.CompetitionLogging.Live.loggingLayer
import compman.compsrv.model.Mapping
import compman.compsrv.model.commands.CommandDTO
import zio.{ExitCode, URIO, ZEnv, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.kafka.admin.{AdminClient, AdminClientSettings}
import zio.kafka.consumer.{Consumer, ConsumerSettings, Offset, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.logging.Logging

import java.util.UUID
import scala.util.{Failure, Success, Try}

object CommandProcessorMain extends zio.App {

  object Live {
    implicit val commandMapping: Mapping.CommandMapping[LIO] = Mapping.CommandMapping.live
    implicit val eventMapping: Mapping.EventMapping[LIO]     = model.Mapping.EventMapping.live
    implicit val idOperations: IdOperations[LIO]             = IdOperations.live
    implicit val eventOperations: EventOperations[LIO]       = EventOperations.live
    implicit val selectInterpreter: Interpreter[LIO]         = Interpreter.asTask
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO] =
      compman.compsrv.logic.logging.CompetitionLogging.Live.live
  }

  type PipelineEnvironment = LiveEnv with Console

  def createProgram(appConfig: AppConfig): ZIO[zio.ZEnv, Any, Any] = {
    val consumerSettings = ConsumerSettings(appConfig.consumer.brokers)
      .withGroupId(appConfig.consumer.groupId)
      .withClientId("client")
      .withCloseTimeout(30.seconds)
      .withPollTimeout(10.millis)
      .withProperty("member.id", UUID.randomUUID().toString)
      .withProperty("enable.auto.commit", "false")
      .withProperty("auto.offset.reset", "earliest")

    val adminSettings    = AdminClientSettings(appConfig.producer.brokers)
    val consumerLayer    = Consumer.make(consumerSettings).toLayer
    val producerSettings = ProducerSettings(appConfig.producer.brokers)
    val producerLayer = Producer.make(producerSettings).toLayer
    val snapshotLayer = SnapshotService.live(appConfig.snapshotConfig.databasePath).toLayer
    val layers        = consumerLayer ++ producerLayer ++ snapshotLayer
    val adminManaged  = AdminClient.make(adminSettings)

    val program: ZIO[PipelineEnvironment, Any, Any] = {
      adminManaged.use { admin =>
        ActorSystem("command-processor").use { actorSystem =>
          for {
            _ <- admin.createTopic(AdminClient.NewTopic(appConfig.commandProcessor.competitionNotificationsTopic, 1, 1))
              .foldCause(err => Logging.error("Error while creating topic", err), _ => ZIO.unit)
            kafkaSupervisor <- actorSystem.make(
              "kafkaSupervisor",
              ActorConfig(),
              None,
              KafkaSupervisor.behavior[PipelineEnvironment](
                appConfig.producer.brokers
              )
            )
            commandProcessorOperationsFactory = CommandProcessorOperationsFactory
              .live(appConfig.commandProcessor)
            suervisor <- actorSystem.make(
              "command-processor-supervisor",
              ActorConfig(),
              (),
              CompetitionProcessorSupervisorActor.behavior(commandProcessorOperationsFactory, appConfig.commandProcessor, kafkaSupervisor)
            )
            res <- Consumer
              .subscribeAnd(Subscription.topics(appConfig.consumer.commandsTopic))
              .plainStream(Serde.string, commandDeserializer.asTry)
              .mapM(record => {
                val tryValue: Try[CommandDTO] = record.record.value()
                val offset: Offset = record.offset

                Logging.info(s"Received command: $tryValue") *>
                  (tryValue match {
                    case Failure(exception) => for {
                      _ <- Logging.error("Error during deserialization")
                      _ <- logError(exception)
                    } yield offset
                    case Success(value) =>
                      (suervisor ! CompetitionProcessorSupervisorActor.CommandReceived(record.key, value)).as(offset)
                  })
              }).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain
          } yield res
        }
      }
    }
    program.provideSomeLayer[ZEnv](Clock.live ++ Blocking.live ++ layers ++ loggingLayer)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for { program <- AppConfig.load().flatMap(config => createProgram(config)) } yield program).exitCode
  }
}
