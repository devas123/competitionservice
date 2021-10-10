package compman.compsrv.query.service.repository

import com.typesafe.config.ConfigFactory
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZLayer
import zio.logging.Logging

object CompetitionOperationsStageOperationsTest extends DefaultRunnableSpec with TestEntities {
  type Env = Logging
  private val config: CassandraContextConfig = CassandraContextConfig(ConfigFactory.load().getConfig("ctx"))
  private val cassandraEnvironment =
    CassandraZioSession(config.cluster, config.keyspace, config.preparedStatementCacheSize)
  private implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
  private implicit val queryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
    .live(cassandraEnvironment)
  private implicit val updateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
    .live(cassandraEnvironment)
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  override def spec: ZSpec[Any, Throwable] = suite("competition operations")(testM("should save stage") {
    (for { _ <- CompetitionUpdateOperations[LIO].addStage(stageDescriptor) } yield assert(Some(()))(isSome))
      .provideLayer(layers)
  }) @@ sequential
}
