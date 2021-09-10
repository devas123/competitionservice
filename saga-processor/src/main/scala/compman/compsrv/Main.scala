package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.SerdeApi.{byteSerializer, commandDeserializer}
import compman.compsrv.logic.Operations._
import compman.compsrv.logic._
import compman.compsrv.logic.actors.CompetitionProcessorActor.{Context, LiveEnv}
import compman.compsrv.logic.actors.Messages.ProcessCommand
import compman.compsrv.logic.actors._
import compman.compsrv.logic.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.logic.logging.CompetitionLogging.Live.loggingLayer
import compman.compsrv.model.Mapping
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.admin.{AdminClient, AdminClientSettings}
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.{ExitCode, Ref, Task, URIO, ZIO}

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

  def createProgram(
    appConfig: AppConfig,
    refActorsMap: Ref[Map[String, CompetitionProcessorActorRef[PipelineEnvironment]]]
  ): ZIO[Any with Clock with Blocking, Any, Any] = {
    val consumerSettings = ConsumerSettings(appConfig.consumer.brokers).withGroupId(appConfig.consumer.groupId)
      .withClientId("client").withCloseTimeout(30.seconds).withPollTimeout(10.millis)
      .withProperty("enable.auto.commit", "false").withProperty("auto.offset.reset", "earliest")

    val producerSettings = ProducerSettings(appConfig.producer.brokers)
    val adminSettings = AdminClientSettings(appConfig.producer.brokers)
    val admin = AdminClient.make(adminSettings)
    val consumerLayer = Consumer.make(consumerSettings).toLayer
    val producerLayer = Producer.make[Any, String, Array[Byte]](producerSettings, Serde.string, byteSerializer).toLayer
    val snapshotLayer = SnapshotService.live(appConfig.snapshotConfig.databasePath).toLayer
    val layers = consumerLayer ++ producerLayer ++ snapshotLayer
    val program: ZIO[PipelineEnvironment, Any, Any] = Consumer
      .subscribeAnd(Subscription.topics(appConfig.consumer.commandsTopic)).plainStream(Serde.string, commandDeserializer)
      .mapM(record => {
        val actorConfig: ActorConfig = ActorConfig(record.key, s"${appConfig.commandProcessor.eventsTopicPrefix}_${record.key}", appConfig.commandProcessor.actorIdleTimeoutMillis)
        val context = Context(refActorsMap, record.key)
        (for {
          map <- refActorsMap.get
          actor <-
            if (map.contains(record.key)) Task.effectTotal(map(record.key))
            else {
              admin.use { adm =>
                for {
                  commandProcessorOperations <- createCommandProcessorConfig[PipelineEnvironment](adm)
                  act <- CompetitionProcessorActor(actorConfig, commandProcessorOperations, context)(() =>
                    refActorsMap.update(m => m - record.key) *> commandProcessorOperations.sendNotifications(Seq(CompetitionProcessingStopped(record.key)))
                  )
                } yield act
              }
            }
          _ <- refActorsMap.set(map + (record.key -> actor))
          _ <- actor ! ProcessCommand(record.value)
        } yield ()).as(record)
      }).map(_.offset).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain

    program.provideSomeLayer(Clock.live ++ Blocking.live ++ layers ++ loggingLayer)
  }

  private def createCommandProcessorConfig[E](adm: AdminClient) = CommandProcessorOperations[E](adm)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for {
      competitionProcessorsMap <- Ref.make(Map.empty[String, CompetitionProcessorActorRef[PipelineEnvironment]])
      program <- AppConfig.load().flatMap(config => createProgram(config, competitionProcessorsMap))
    } yield program).exitCode
  }
}
