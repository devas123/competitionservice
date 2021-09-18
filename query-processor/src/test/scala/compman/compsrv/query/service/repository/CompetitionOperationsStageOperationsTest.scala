package compman.compsrv.query.service.repository

import com.typesafe.config.ConfigFactory
import compman.compsrv.logic.logging.CompetitionLogging
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZLayer
import zio.logging.Logging

object CompetitionOperationsStageOperationsTest extends DefaultRunnableSpec with TestEntities {
  type Env = QuillCassandraEnvironment with Logging
  private val cassandraEnvironment = CassandraZioSession
    .fromContextConfig(CassandraContextConfig(ConfigFactory.load().getConfig("ctx")))
  private implicit val queryOperations: CompetitionQueryOperations[RepoIO] = CompetitionQueryOperations
    .live(CompetitionLogging.Live.live)
  private implicit val updateOperations: CompetitionUpdateOperations[RepoIO] = CompetitionUpdateOperations
    .live(CompetitionLogging.Live.live)
  val layers: ZLayer[Any, Throwable, Env] = cassandraEnvironment ++ CompetitionLogging.Live.loggingLayer
  override def spec: ZSpec[Any, Throwable] = suite("competition operations")(
    testM("should save stage") {
      (for {
        _ <- CompetitionUpdateOperations[RepoIO].addStage(stageDescriptor)
      } yield assert(Some(()))(isSome)).provideLayer(layers)
    }

  ) @@ sequential
}
