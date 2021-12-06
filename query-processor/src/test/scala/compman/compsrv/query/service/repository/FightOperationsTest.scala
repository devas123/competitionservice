package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition.FightStatus
import org.testcontainers.containers.MongoDBContainer
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.TestAspect.sequential
import zio.test._
import zio.{ZLayer, ZManaged}

import java.util.UUID

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
          _ <- FightUpdateOperations[LIO].updateFightScoresAndResultAndStatus(competitionId)(fightId, scores, fightResult, FightStatus.FINISHED)
          loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(categoryId, fightId)
          _ <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
        } yield assert(loadedFight)(isSome) && assert(loadedFight.get.scores)(isNonEmpty) &&
          assert(loadedFight.get.scores.size)(equalTo(2)) && assert(loadedFight.get.fightResult)(isSome) &&
          assert(loadedFight.get.fightResult.get.reason)(equalTo(fightResult.reason)) &&
          assert(loadedFight.get.fightResult.get.winnerId)(equalTo(fightResult.winnerId)) &&
          assert(loadedFight.get.status.get)(equalTo(FightStatus.FINISHED)) &&
          assert(loadedFight.get.fightResult.get.resultTypeId)(equalTo(fightResult.resultTypeId))
          )
          .provideLayer(layers)
      }
    },
    testM("Should get fights by ids") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _ <- FightUpdateOperations[LIO].addFight(fight)
          fight2Id = UUID.randomUUID().toString
          _ <- FightUpdateOperations[LIO].addFight(fight.copy(id = fight2Id))
          loadedFight <- FightQueryOperations[LIO].getFightsByIds(competitionId)(categoryId, Set(fightId, fight2Id))
          _ <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
        } yield assert(loadedFight)(hasSize(equalTo(2))))
          .provideLayer(layers)
      }
    }
  ) @@ sequential
}
