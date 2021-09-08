package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.SerdeApi.{byteSerialized, commandDeserializer}
import compman.compsrv.logic.Operations._
import compman.compsrv.logic._
import compman.compsrv.logic.actors.CompetitionProcessor.{Context, LiveEnv}
import compman.compsrv.logic.actors.Messages.ProcessCommand
import compman.compsrv.logic.actors._
import compman.compsrv.logic.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.logic.logging.CompetitionLogging.Live.loggingLayer
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.{ExitCode, Ref, Task, URIO, ZIO}

object Main extends zio.App {

  object Live {
    implicit val commandMapping: Mapping.CommandMapping[LIO] = compman.compsrv.logic.Mapping.CommandMapping.live
    implicit val eventMapping: Mapping.EventMapping[LIO]     = compman.compsrv.logic.Mapping.EventMapping.live
    implicit val idOperations: IdOperations[LIO]             = IdOperations.live
    implicit val eventOperations: EventOperations[LIO]       = EventOperations.live
    implicit val selectInterpreter: Interpreter[LIO]         = Interpreter.asTask
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO] =
      compman.compsrv.logic.logging.CompetitionLogging.Live.live
  }

  type PipelineEnvironment = LiveEnv

  def createProgram(
    appConfig: AppConfig,
    refActorsMap: Ref[Map[String, CompetitionProcessorActorRef]]
  ): ZIO[Any with Clock with Blocking, Any, Any] = {
    val consumerSettings = ConsumerSettings(appConfig.consumer.brokers).withGroupId(appConfig.consumer.groupId)
      .withClientId("client").withCloseTimeout(30.seconds).withPollTimeout(10.millis)
      .withProperty("enable.auto.commit", "false").withProperty("auto.offset.reset", "earliest")

    val producerSettings = ProducerSettings(appConfig.producer.brokers)

    val consumerLayer = Consumer.make(consumerSettings).toLayer
    val producerLayer = Producer.make[Any, String, Array[Byte]](producerSettings, Serde.string, byteSerialized).toLayer
    val snapshotLayer = SnapshotService.live(appConfig.snapshotConfig.databasePath).toLayer
    val layers        = consumerLayer ++ producerLayer ++ snapshotLayer
    val program: ZIO[PipelineEnvironment, Any, Any] = Consumer
      .subscribeAnd(Subscription.topics(appConfig.consumer.topic)).plainStream(Serde.string, commandDeserializer)
      .mapM(record => {
        val actorConfig: ActorConfig = createActorConfig(record.key)
        val context                  = Context(refActorsMap, record.key)
        (for {
          map <- refActorsMap.get
          commandProcessorOperations   <- createCommandProcessorConfig[PipelineEnvironment]
          actor <-
            if (map.contains(record.key)) Task.effectTotal(map(record.key))
            else {
              CompetitionProcessor(actorConfig, commandProcessorOperations, context)(() =>
                refActorsMap.update(m => m - record.key) *> commandProcessorOperations.sendNotifications(Seq(CompetitionProcessingStopped(record.key)))
              )
            }
          _ <- refActorsMap.set(map + (record.key -> actor))
          _ <- actor ! ProcessCommand(record.value)
        } yield ()).as(record)
      }).map(_.offset).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain

    program.provideSomeLayer(Clock.live ++ Blocking.live ++ layers ++ loggingLayer)
  }

  private def createActorConfig(competitionId: String) = ActorConfig(competitionId)
  private def createCommandProcessorConfig[E]            = CommandProcessorOperations[E]()
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for {
      competitionProcessorsMap <- Ref.make(Map.empty[String, CompetitionProcessorActorRef])
      program                  <- AppConfig.load().flatMap(config => createProgram(config, competitionProcessorsMap))
    } yield program).exitCode
  }
}
