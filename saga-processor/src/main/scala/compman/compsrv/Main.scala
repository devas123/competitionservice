package compman.compsrv

import compman.compsrv.config.AppConfig
import compman.compsrv.logic.{Mapping, StateOperations}
import compman.compsrv.logic.CommunicationApi.{deserializer, serializer}
import compman.compsrv.logic.Operations._
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.repository.CompetitionStateCrudRepository
import org.rocksdb.RocksDB
import zio.{Chunk, ExitCode, Ref, Task, URIO, ZIO}
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
    implicit val commandMapping: Mapping.CommandMapping[Task] =
      compman.compsrv.logic.Mapping.CommandMapping.live
    implicit val eventMapping: Mapping.EventMapping[Task] =
      compman.compsrv.logic.Mapping.EventMapping.live
    implicit val stateOperations: StateOperations.Service[Task] = StateOperations.Service.live
    implicit val idOperations: IdOperations[Task]               = IdOperations.live
    implicit val eventOperations: EventOperations[Task]         = EventOperations.live
  }

  type PipelineEnvironment =
    Clock with Blocking with Logging with Consumer with Producer[Any, String, EventDTO]

  def createProgram(
      appConfig: AppConfig,
      rocksDBMap: Ref[Map[String, CompetitionStateCrudRepository[Task]]]
  ): ZIO[Any with Clock with Blocking, Any, Any] = {
    import Live._
    val consumerSettings = ConsumerSettings(appConfig.consumer.brokers)
      .withGroupId(appConfig.consumer.groupId)
      .withClientId("client")
      .withCloseTimeout(30.seconds)
      .withPollTimeout(10.millis)
      .withProperty("enable.auto.commit", "false")
      .withProperty("auto.offset.reset", "earliest")

    val producerSettings = ProducerSettings(appConfig.producer.brokers)

    val loggingLayer = Slf4jLogger.make { (context, message) =>
       val correlationId = context.get(LogAnnotation.CorrelationId)
       "[correlation-id = %s] %s".format(correlationId, message)
    }

    val consumerLayer = Consumer.make(consumerSettings).toLayer
    val producerLayer =
      Producer.make[Any, String, EventDTO](producerSettings, Serde.string, serializer).toLayer
    val layers = consumerLayer ++ producerLayer
    import zio.interop.catz._
    val program: ZIO[PipelineEnvironment, Any, Any] =
      zio.logging.log.info("Test") *>
        Consumer
          .subscribeAnd(Subscription.topics(appConfig.consumer.topic))
          .plainStream(Serde.string, deserializer)
          .mapM(record => {
            (
              for {
                rocksDb <- rocksDBMap.modify(map => {
                  val db = map.getOrElse(
                    record.key,
                    CompetitionStateCrudRepository.createLive(RocksDB.open(record.key))
                  )
                  (db, map + (record.key -> db))
                })
                records <- mapRecord[Task](appConfig, rocksDb, record)
                produce <-
                  records match {
                    case x @ Left(_) =>
                      ZIO.fromEither(x)
                    case Right(value) =>
                      val deleted = value.exists(_.value().getType == EventType.COMPETITION_DELETED)
                      rocksDBMap.update(map => if (deleted) map - record.key else map) *> Producer.produceChunk[Any, String, EventDTO](Chunk.fromIterable(value))
                  }
              } yield produce
            ).as(record)
          })
          .map(_.offset)
          .aggregateAsync(Consumer.offsetBatches)
          .mapM(_.commit)
          .runDrain

    program.provideSomeLayer(Clock.live ++ Blocking.live ++ loggingLayer ++ layers)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (
      for {
        rocksDBMap <- Ref.make(Map.empty[String, CompetitionStateCrudRepository[Task]])
        program    <- AppConfig.load().flatMap(config => createProgram(config, rocksDBMap))
      } yield program
    ).exitCode
  }
}
