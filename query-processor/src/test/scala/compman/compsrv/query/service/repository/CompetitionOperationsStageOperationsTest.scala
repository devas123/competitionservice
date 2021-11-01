package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZLayer
import zio.logging.Logging

object CompetitionOperationsStageOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  import EmbeddedMongoDb._
  override def spec: ZSpec[Any, Throwable] = suite("competition operations")(testM("should save stage") {
    getCassandraResource.use { _ =>
      (for {_ <- CompetitionUpdateOperations[LIO].addStage(stageDescriptor)} yield assert(Some(()))(isSome))
        .provideLayer(layers)
    }
  }) @@ sequential
}
