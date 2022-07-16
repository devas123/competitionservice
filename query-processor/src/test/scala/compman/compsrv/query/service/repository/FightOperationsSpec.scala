package compman.compsrv.query.service.repository

import cats.effect.IO
import compman.compsrv.model.extensions.InstantOps
import compman.compsrv.query.model.{FightOrderUpdateExtended, Mat}
import compman.compsrv.SpecBase
import compman.compsrv.logic.actors.behavior.WithIORuntime
import compservice.model.protobuf.eventpayload.FightOrderUpdate
import compservice.model.protobuf.model.FightStatus

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Date, UUID}
import scala.util.Using

class FightOperationsSpec extends SpecBase with TestEntities with EmbeddedMongoDb with WithIORuntime {
  test("Should save and load fight by id") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _           <- FightUpdateOperations[IO].addFight(fight)
        loadedFight <- FightQueryOperations[IO].getFightById(competitionId)(fightId)
        _           <- FightUpdateOperations[IO].removeFightsByCompetitionId(competitionId)
      } yield assert(loadedFight.isDefined)).unsafeRunSync()
    }
  }
  test("Should update fight result and score") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _ <- FightUpdateOperations[IO].addFight(fight)
        _ <- FightUpdateOperations[IO]
          .updateFightScoresAndResultAndStatus(competitionId)(fightId, scores.toList, fightResult, FightStatus.FINISHED)
        loadedFight <- FightQueryOperations[IO].getFightById(competitionId)(fightId)
        _           <- FightUpdateOperations[IO].removeFightsByCompetitionId(competitionId)
        _ <- IO {
          assert(loadedFight.isDefined)
          assert(loadedFight.get.scores.nonEmpty)
          assert(loadedFight.get.scores.size == 2)
          assert(loadedFight.get.fightResult.isDefined)
          assert(loadedFight.get.scores.forall(s => s.competitorFirstName.isDefined && s.competitorLastName.isDefined))
          assert(loadedFight.get.fightResult.isDefined)
          assert(loadedFight.get.fightResult.get.reason == fightResult.reason)
          assert(loadedFight.get.fightResult.get.winnerId == fightResult.winnerId)
          assert(loadedFight.get.status.get == FightStatus.FINISHED)
          assert(loadedFight.get.fightResult.get.resultTypeId == fightResult.resultTypeId)
        }
      } yield ()).unsafeRunSync()

    }
  }
  test("Should get fights by ids") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _ <- FightUpdateOperations[IO].addFight(fight)
        fight2Id = UUID.randomUUID().toString
        _           <- FightUpdateOperations[IO].addFight(fight.copy(id = fight2Id))
        loadedFight <- FightQueryOperations[IO].getFightsByIds(competitionId)(Set(fightId, fight2Id))
        _           <- FightUpdateOperations[IO].removeFightsByCompetitionId(competitionId)
      } yield assert(loadedFight.size == 2)).unsafeRunSync()

    }
  }
  test("Should update fight order") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _ <- FightUpdateOperations[IO].removeFightsByCompetitionId(competitionId)
        fights <-
          IO(List(fight, fight.copy(id = UUID.randomUUID().toString), fight.copy(id = UUID.randomUUID().toString)))
        _ <- FightUpdateOperations[IO].addFights(fights)
        newMat         = Mat(UUID.randomUUID().toString, "newMat", 100)
        newStartTime   = Instant.now().plus(10, ChronoUnit.HOURS)
        newNumberOnMat = 50
        updates = fights.map(f => {
          FightOrderUpdateExtended(
            competitionId,
            FightOrderUpdate().withFightId(f.id).withMatId(newMat.matId).withStartTime(newStartTime.asTimestamp)
              .withNumberOnMat(newNumberOnMat),
            newMat
          )
        })
        _             <- FightUpdateOperations[IO].updateFightOrderAndMat(updates)
        updatedFights <- FightQueryOperations[IO].getFightsByIds(competitionId)(fights.map(_.id).toSet)
        _ <- IO {
          assert(updatedFights.size == 3)
          assert(updatedFights.forall(f =>
            f.matId.contains(newMat.matId) && f.matName.contains(newMat.name) && f.matOrder.contains(newMat.matOrder) &&
              f.startTime.contains(Date.from(newStartTime)) && f.numberOnMat.contains(newNumberOnMat)
          ))
        }
      } yield ()).unsafeRunSync()

    }
  }
}
