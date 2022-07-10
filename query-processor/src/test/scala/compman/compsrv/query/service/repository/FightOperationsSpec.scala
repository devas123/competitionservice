package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.extensions.InstantOps
import compman.compsrv.query.model.{FightOrderUpdateExtended, Mat}
import compservice.model.protobuf.eventpayload.FightOrderUpdate
import compservice.model.protobuf.model.FightStatus
import org.testcontainers.containers.MongoDBContainer
import zio.logging.Logging
import zio.test.Assertion._
import zio.test.TestAspect.sequential
import zio.test._
import zio.{ZIO, ZLayer, ZManaged}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Date, UUID}

object FightOperationsSpec extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val mongoLayer: ZManaged[Any, Nothing, MongoDBContainer] = embeddedMongo()
  val layers: ZLayer[Any, Throwable, Env] = Compman.compsrv.interop.loggingLayer

  override def spec: ZSpec[Environment, Throwable] = suite("Fight operations")(
    testM("Should save and load fight by id") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _ <- FightUpdateOperations[LIO].addFight(fight)
          loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(fightId)
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
          _ <- FightUpdateOperations[LIO].updateFightScoresAndResultAndStatus(competitionId)(fightId, scores.toList, fightResult, FightStatus.FINISHED)
          loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(fightId)
          _ <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
        } yield assert(loadedFight)(isSome) && assertTrue(loadedFight.get.scores.nonEmpty) &&
          assertTrue(loadedFight.get.scores.size == 2) && assert(loadedFight.get.fightResult)(isSome) &&
          assertTrue(loadedFight.get.scores.forall(s => s.competitorFirstName.isDefined && s.competitorLastName.isDefined)) &&
          assert(loadedFight.get.fightResult)(isSome) &&
          assertTrue(loadedFight.get.fightResult.get.reason == fightResult.reason) &&
          assertTrue(loadedFight.get.fightResult.get.winnerId == fightResult.winnerId) &&
          assertTrue(loadedFight.get.status.get == FightStatus.FINISHED) &&
          assertTrue(loadedFight.get.fightResult.get.resultTypeId == fightResult.resultTypeId)
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
          loadedFight <- FightQueryOperations[LIO].getFightsByIds(competitionId)(Set(fightId, fight2Id))
          _ <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
        } yield assert(loadedFight)(hasSize(equalTo(2))))
          .provideLayer(layers)
      }
    },
    testM("Should update fight order") {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        (for {
          _ <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
          fights <- ZIO.effectTotal(List(fight, fight.copy(id = UUID.randomUUID().toString), fight.copy(id = UUID.randomUUID().toString)))
          _ <- FightUpdateOperations[LIO].addFights(fights)
          newMat = Mat(UUID.randomUUID().toString, "newMat", 100)
          newStartTime = Instant.now().plus(10, ChronoUnit.HOURS)
          newNumberOnMat = 50
          updates = fights.map(f => {
            FightOrderUpdateExtended(competitionId, FightOrderUpdate().withFightId(f.id)
              .withMatId(newMat.matId)
              .withStartTime(newStartTime.asTimestamp)
              .withNumberOnMat(newNumberOnMat), newMat)
          })
          _ <- FightUpdateOperations[LIO].updateFightOrderAndMat(updates)
          updatedFights <- FightQueryOperations[LIO].getFightsByIds(competitionId)(fights.map(_.id).toSet)
        } yield assert(updatedFights)(hasSize(equalTo(3))) && assertTrue(updatedFights.forall(f =>
          f.matId.contains(newMat.matId)
            && f.matName.contains(newMat.name)
            && f.matOrder.contains(newMat.matOrder)
            && f.startTime.contains(Date.from(newStartTime))
            && f.numberOnMat.contains(newNumberOnMat))))
          .provideLayer(layers)
      }
    }
  ) @@ sequential
}
