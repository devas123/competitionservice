package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.SerdeApi.{byteSerializer, commandDeserializer}
import compman.compsrv.logic.Operations._
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.CompetitionProcessorActor.{LiveEnv, Message, ProcessCommand}
import compman.compsrv.logic.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.logic.logging.CompetitionLogging.Live.loggingLayer
import compman.compsrv.model.Mapping
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{ExitCode, Task, URIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.admin.{AdminClient, AdminClientSettings}
import zio.kafka.consumer.{Consumer, ConsumerSettings, Offset, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.logging.Logging

import java.util.UUID
import scala.util.{Failure, Success, Try}

object Main extends zio.App {

  object Live {
    implicit val commandMapping: Mapping.CommandMapping[LIO] = Mapping.CommandMapping.live
    implicit val eventMapping: Mapping.EventMapping[LIO]     = model.Mapping.EventMapping.live
    implicit val idOperations: IdOperations[LIO]             = IdOperations.live
    implicit val eventOperations: EventOperations[LIO]       = EventOperations.live
    implicit val selectInterpreter: Interpreter[LIO]         = Interpreter.asTask
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO] =
      compman.compsrv.logic.logging.CompetitionLogging.Live.live
  }

  type PipelineEnvironment = LiveEnv

  def createProgram(appConfig: AppConfig): ZIO[Any with Clock with Blocking, Any, Any] = {
    val consumerSettings = ConsumerSettings(appConfig.consumer.brokers).withGroupId(appConfig.consumer.groupId)
      .withClientId("client").withCloseTimeout(30.seconds).withPollTimeout(10.millis)
      .withProperty("enable.auto.commit", "false").withProperty("auto.offset.reset", "earliest")

    val adminSettings    = AdminClientSettings(appConfig.producer.brokers)
    val admin            = AdminClient.make(adminSettings)
    val consumerLayer    = Consumer.make(consumerSettings).toLayer
    val producerSettings = ProducerSettings(appConfig.producer.brokers)
    val producerLayer = Producer.make[Any, String, Array[Byte]](producerSettings, Serde.string, byteSerializer).toLayer
    val snapshotLayer = SnapshotService.live(appConfig.snapshotConfig.databasePath).toLayer
    val layers        = consumerLayer ++ producerLayer ++ snapshotLayer
    val program: ZIO[PipelineEnvironment, Any, Any] = for {
      actorSystem <- ActorSystem("saga-processor")
      res <- Consumer.subscribeAnd(Subscription.topics(appConfig.consumer.commandsTopic))
        .plainStream(Serde.string, commandDeserializer.asTry).mapM(record => {
          val tryValue: Try[CommandDTO] = record.record.value()
          val offset: Offset            = record.offset

          tryValue match {
            case Failure(exception) => for {
                _ <- Logging.error("Error during deserialization")
                _ <- logError(exception)
              } yield offset
            case Success(value) => (for {
                _ <- Logging.info(s"Received command: $value")
                actor <- admin.use { adm =>
                  for {
                    act <- actorSystem.select[Message](s"CompetitionProcessor-${record.key}").foldM(
                      _ =>
                        for {
                          _ <- Logging.info(s"Creating new actor for competition: ${record.key}")
                          commandProcessorOperations <- createCommandProcessorConfig[PipelineEnvironment](
                            adm,
                            consumerSettings
                              .withClientId(UUID.randomUUID().toString)
                              .withGroupId(UUID.randomUUID().toString)
                          )
                          initialState <- commandProcessorOperations.getStateSnapshot(record.key) >>=
                            (_.map(Task(_)).getOrElse(commandProcessorOperations.createInitialState(record.key)))
                          a <- actorSystem.make(
                            s"CompetitionProcessor-${record.key}",
                            ActorConfig(),
                            initialState,
                            CompetitionProcessorActor
                              .behavior(commandProcessorOperations, record.key, s"${record.key}-events")
                          )
                        } yield a,
                      actor => Logging.info(s"Found existing actor for ${record.key}") *> ZIO.effect(actor)
                    )
                  } yield act
                }
                _ <- for {
                  _ <- Logging.info(s"Sending command $value to existing actor")
                  _ <- actor ! ProcessCommand(value)
                } yield ()
              } yield ()).as(offset)
          }
        }).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain
    } yield res
    program.provideSomeLayer(Clock.live ++ Blocking.live ++ layers ++ loggingLayer)
  }

  private def createCommandProcessorConfig[E](adm: AdminClient, consumerSettings: ConsumerSettings) =
    CommandProcessorOperations[E](adm, consumerSettings)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for { program <- AppConfig.load().flatMap(config => createProgram(config)) } yield program).exitCode
  }
}
