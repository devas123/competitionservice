package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.SerdeApi.{byteSerializer, commandDeserializer}
import compman.compsrv.logic.Operations._
import compman.compsrv.logic.actors.CompetitionProcessorActor.LiveEnv
import compman.compsrv.logic.actors._
import compman.compsrv.logic.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging.Live.loggingLayer
import compman.compsrv.logic.logging.CompetitionLogging.{LIO, logError}
import compman.compsrv.model.Mapping
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.admin.AdminClientSettings
import zio.kafka.consumer.{Consumer, ConsumerSettings, Offset, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.{ExitCode, URIO, ZIO}

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
    val consumerLayer    = Consumer.make(consumerSettings).toLayer
    val producerSettings = ProducerSettings(appConfig.producer.brokers)
    val producerLayer = Producer.make[Any, String, Array[Byte]](producerSettings, Serde.string, byteSerializer).toLayer
    val snapshotLayer = SnapshotService.live(appConfig.snapshotConfig.databasePath).toLayer
    val layers        = consumerLayer ++ producerLayer ++ snapshotLayer
    val program: ZIO[PipelineEnvironment, Any, Any] = for {
      actorSystem <- ActorSystem("saga-processor")
      suervisor <- actorSystem.make("saga-processor-supervisor", ActorConfig(), (), CompetitionProcessorSupervisorActor.behavior(consumerSettings, adminSettings))
      res <- Consumer.subscribeAnd(Subscription.topics(appConfig.consumer.commandsTopic))
        .plainStream(Serde.string, commandDeserializer.asTry).mapM(record => {
        val tryValue: Try[CommandDTO] = record.record.value()
        val offset: Offset = record.offset

        tryValue match {
          case Failure(exception) => for {
            _ <- Logging.error("Error during deserialization")
            _ <- logError(exception)
          } yield offset
          case Success(value) => (suervisor ! CompetitionProcessorSupervisorActor.CommandReceived(record.key, value)).as(offset)
        }
      }).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain
    } yield res
    program.provideSomeLayer(Clock.live ++ Blocking.live ++ layers ++ loggingLayer)
  }


  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for { program <- AppConfig.load().flatMap(config => createProgram(config)) } yield program).exitCode
  }
}
