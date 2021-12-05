package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import org.testcontainers.containers.MongoDBContainer
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.TestAspect.sequential
import zio.test._
import zio.{ZLayer, ZManaged}

object FightOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val mongoLayer: ZManaged[Any, Nothing, MongoDBContainer] = embeddedMongo()
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  override def spec: ZSpec[Any, Throwable] = suite("Fight operations")(
    testM("Should save and load fight by id") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _ <- FightUpdateOperations[LIO].addFight(fight)
          loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(categoryId, fightId)
          _ <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
        } yield assert(loadedFight)(isSome)).provideLayer(layers)
      }
    },
    testM("Should update fight result and score") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _ <- FightUpdateOperations[LIO].addFight(fight)
          _ <- FightUpdateOperations[LIO].updateFightScoresAndResult(competitionId)(fightId, scores, fightResult)
          loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(categoryId, fightId)
          _ <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
        } yield assert(loadedFight)(isSome) && assert(loadedFight.get.scores)(isNonEmpty) &&
          assert(loadedFight.get.scores.size)(equalTo(2)) && assert(loadedFight.get.fightResult)(isSome) &&
          assert(loadedFight.get.fightResult.get.reason)(equalTo(fightResult.reason)) &&
          assert(loadedFight.get.fightResult.get.winnerId)(equalTo(fightResult.winnerId)) &&
          assert(loadedFight.get.fightResult.get.resultTypeId)(equalTo(fightResult.resultTypeId))
          )
          .provideLayer(layers)
      }
    }
  ) @@ sequential
}
