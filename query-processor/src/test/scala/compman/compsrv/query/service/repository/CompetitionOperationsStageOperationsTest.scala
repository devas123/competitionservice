package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import org.testcontainers.containers.MongoDBContainer
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{ZLayer, ZManaged}

object CompetitionOperationsStageOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  val mongoLayer: ZManaged[Any, Nothing, MongoDBContainer] = embeddedMongo()

  override def spec: ZSpec[Any, Throwable] = suite("competition operations")(testM("should save stage") {
    mongoLayer.use { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _ <- CompetitionUpdateOperations[LIO].removeCompetitionState(competitionId)
        _ <- CompetitionUpdateOperations[LIO].addCompetitionProperties(competitionProperties)
        _ <- CompetitionUpdateOperations[LIO].addStage(stageDescriptor)
        stage <- CompetitionQueryOperations[LIO].getStageById(competitionId)(categoryId, stageId)
      } yield assert(stage)(isSome))
        .provideLayer(layers)
    }
  }) @@ sequential
}
