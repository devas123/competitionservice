package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.SerdeApi.{byteSerializer, commandDeserializer}
import compman.compsrv.logic.Operations._
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.CompetitionProcessorActor.{LiveEnv, Message, ProcessCommand}
import compman.compsrv.logic.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.logic.logging.CompetitionLogging.Live.loggingLayer
import compman.compsrv.model.Mapping
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{ExitCode, Task, URIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.admin.{AdminClient, AdminClientSettings}
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde

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

    val producerSettings = ProducerSettings(appConfig.producer.brokers)
    val adminSettings    = AdminClientSettings(appConfig.producer.brokers)
    val admin            = AdminClient.make(adminSettings)
    val consumerLayer    = Consumer.make(consumerSettings).toLayer
    val producerLayer = Producer.make[Any, String, Array[Byte]](producerSettings, Serde.string, byteSerializer).toLayer
    val snapshotLayer = SnapshotService.live(appConfig.snapshotConfig.databasePath).toLayer
    val layers        = consumerLayer ++ producerLayer ++ snapshotLayer
    val program: ZIO[PipelineEnvironment, Any, Any] = Consumer
      .subscribeAnd(Subscription.topics(appConfig.consumer.commandsTopic))
      .plainStream(Serde.string, commandDeserializer).mapM(record => {
        (for {
          actorSystem <- ActorSystem("saga-processor")
          actor <- admin.use { adm =>
            for {
              commandProcessorOperations <- createCommandProcessorConfig[PipelineEnvironment](adm)
              initialState <- commandProcessorOperations.getStateSnapshot(record.key) >>=
                (_.map(Task(_)).getOrElse(commandProcessorOperations.createInitialState(record.key)))

              act <- actorSystem.select[Message](s"CompetitionProcessor-${record.key}").foldM(_ => actorSystem.make(
                s"CompetitionProcessor-${record.key}",
                ActorConfig(),
                initialState,
                CompetitionProcessorActor.behavior(commandProcessorOperations, record.key, s"${record.key}-events")
              ), actor => ZIO.effect(actor))
            } yield act
          }
          _ <- actor ! ProcessCommand(record.value)
        } yield ()).as(record)
      }).map(_.offset).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain

    program.provideSomeLayer(Clock.live ++ Blocking.live ++ layers ++ loggingLayer)
  }

  private def createCommandProcessorConfig[E](adm: AdminClient) = CommandProcessorOperations[E](adm)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for { program <- AppConfig.load().flatMap(config => createProgram(config)) } yield program).exitCode
  }
}
