package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.SerdeApi.{commandDeserializer, eventSerialized}
import compman.compsrv.logic._
import compman.compsrv.logic.actors.CompetitionProcessor.Context
import compman.compsrv.logic.Operations._
import compman.compsrv.logic.StateOperations.GetStateConfig
import compman.compsrv.logic.actors.{CommandProcessorOperations, CompetitionProcessor, CompetitionProcessorActorRef}
import compman.compsrv.logic.actors.Messages.ProcessCommand
import compman.compsrv.logic.service.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.events.EventDTO
import zio.{ExitCode, Has, Ref, Task, URIO, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.logging.{LogAnnotation, Logging}
import zio.logging.slf4j.Slf4jLogger

object Main extends zio.App {

  object Live {
    implicit val commandMapping: Mapping.CommandMapping[Task]   = compman.compsrv.logic.Mapping.CommandMapping.live
    implicit val eventMapping: Mapping.EventMapping[Task]       = compman.compsrv.logic.Mapping.EventMapping.live
    implicit val stateOperations: StateOperations.Service[Task] = StateOperations.Service.live
    implicit val idOperations: IdOperations[Task]               = IdOperations.live
    implicit val eventOperations: EventOperations[Task]         = EventOperations.live
    implicit val selectInterpreter: Interpreter[Task]           = Interpreter.asTask
  }

  type PipelineEnvironment = Clock with Blocking with Logging with Consumer with Producer[Any, String, EventDTO]

  def createProgram(
    appConfig: AppConfig,
    refActorsMap: Ref[Map[String, CompetitionProcessorActorRef]]
  ): ZIO[Any with Clock with Blocking, Any, Any] = {
    val consumerSettings = ConsumerSettings(appConfig.consumer.brokers).withGroupId(appConfig.consumer.groupId)
      .withClientId("client").withCloseTimeout(30.seconds).withPollTimeout(10.millis)
      .withProperty("enable.auto.commit", "false").withProperty("auto.offset.reset", "earliest")

    val producerSettings = ProducerSettings(appConfig.producer.brokers)

    val loggingLayer = Slf4jLogger.make { (context, message) =>
      val correlationId = context.get(LogAnnotation.CorrelationId)
      "[correlation-id = %s] %s".format(correlationId, message)
    }

    val consumerLayer = Consumer.make(consumerSettings).toLayer
    val producerLayer = Producer.make[Any, String, EventDTO](producerSettings, Serde.string, eventSerialized).toLayer
    val layers        = consumerLayer ++ producerLayer
    val program: ZIO[PipelineEnvironment, Any, Any] = zio.logging.log.info("Test") *>
      Consumer.subscribeAnd(Subscription.topics(appConfig.consumer.topic))
        .plainStream(Serde.string, commandDeserializer).mapM(record => {
          val getStateConfig: GetStateConfig = createGetStateConfig(record.key)
          val commandProcessorConfig         = createCommandProcessorConfig(consumerLayer, producerLayer)
          val context                        = Context(refActorsMap, record.key)
          (for {
            map <- refActorsMap.get
            actor <-
              if (map.contains(record.key)) Task.effectTotal(map(record.key))
              else {
                CompetitionProcessor(record.key, getStateConfig, commandProcessorConfig, context)(() =>
                  refActorsMap.update(m => m - record.key)
                )
              }
            _ <- refActorsMap.set(map + (record.key -> actor))
            _ <- actor ! ProcessCommand(record.value)
          } yield ()).as(record)
        }).map(_.offset).aggregateAsync(Consumer.offsetBatches).mapM(_.commit).runDrain

    program.provideSomeLayer(Clock.live ++ Blocking.live ++ loggingLayer ++ layers)
  }

  private def createGetStateConfig(competitionId: String) = GetStateConfig(competitionId)
  private def createCommandProcessorConfig(
    consumerLayer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]],
    producerLayer: ZLayer[Any, Throwable, Has[Producer.Service[Any, String, EventDTO]]]
  ) = CommandProcessorOperations(consumerLayer, producerLayer)
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for {
      competitionProcessorsMap <- Ref.make(Map.empty[String, CompetitionProcessorActorRef])
      program                  <- AppConfig.load().flatMap(config => createProgram(config, competitionProcessorsMap))
    } yield program).exitCode
  }
}
