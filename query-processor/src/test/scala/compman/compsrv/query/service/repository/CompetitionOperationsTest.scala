package compman.compsrv.query.service.repository

import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model.mapping.DtoMapping
import org.testcontainers.containers.MongoDBContainer
import zio.{ZLayer, ZManaged}
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.sequential

object CompetitionOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val mongoLayer: ZManaged[Any, Nothing, MongoDBContainer] = embeddedMongo()
  val layers: ZLayer[Any, Throwable, Env]                  = CompetitionLogging.Live.loggingLayer

  override def spec: ZSpec[Environment, Failure] = suite("competition operations")(
    testM("should delete competition and query should return none when there are no competitions") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _     <- CompetitionUpdateOperations[LIO].removeCompetitionState(competitionId)
          props <- CompetitionQueryOperations.getCompetitionProperties(competitionId)
        } yield assert(props)(isNone)).provideLayer(layers)
      }
    },
    testM("should save competition") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _     <- CompetitionUpdateOperations[LIO].removeCompetitionState(competitionId)
          _     <- CompetitionUpdateOperations[LIO].addCompetitionProperties(competitionProperties)
          props <- CompetitionQueryOperations.getCompetitionProperties(competitionId)
        } yield assert(props)(isSome)).provideLayer(layers)
      }
    },
    testM("should save and load competitor") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import cats.implicits._
        import context._
        import zio.interop.catz._
        (
          for {
            _ <- CompetitionUpdateOperations[LIO].removeCompetitorsForCompetition(competitionId)
            competitors = CompetitorService.generateRandomCompetitorsForCategory(5, 5, categoryId, competitionId)
            _ <- competitors.traverse(c =>
              for {
                mapped <- DtoMapping.mapCompetitor[LIO](c)
                _      <- CompetitionUpdateOperations[LIO].addCompetitor(mapped)
              } yield ()
            )
            (loadedCompetitorsByCompetitionId, pagination) <- CompetitionQueryOperations[LIO]
              .getCompetitorsByCompetitionId(competitionId)(None)
            loadedCompetitorsByIds <- competitors.traverse(c => CompetitionQueryOperations[LIO].getCompetitorById(competitionId)(c.id)).map(_.mapFilter(identity))
          } yield
            assert(loadedCompetitorsByCompetitionId)(hasSize(equalTo(competitors.size))) &&
            assertTrue(loadedCompetitorsByCompetitionId.forall(c => c.lastName.nonEmpty && c.firstName.nonEmpty)) &&
            assert(loadedCompetitorsByIds)(hasSize(equalTo(competitors.size))) &&
            assertTrue(pagination.totalResults == competitors.size)
        ).provideLayer(layers)
      }
    },
    testM("should save category") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _        <- CompetitionUpdateOperations[LIO].removeCompetitionState(competitionId)
          _        <- CompetitionUpdateOperations[LIO].addCompetitionProperties(competitionProperties)
          _        <- CompetitionUpdateOperations[LIO].addCategory(category)
          category <- CompetitionQueryOperations.getCategoryById(competitionId)(categoryId)
        } yield assert(category)(isSome)).provideLayer(layers)
      }
    }
  ) @@ sequential
}
