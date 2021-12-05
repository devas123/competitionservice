package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.{URIO, ZIO, ZLayer}
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{aroundAll, sequential}

object FightOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  override def spec: ZSpec[Any, Throwable] = suite("Fight operations")(
    testM("Should save and load fight by id") {
      val context = EmbeddedMongoDb.context
      import context._
      (for {
        _           <- FightUpdateOperations[LIO].addFight(fight)
        loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(categoryId, fightId)
        _           <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
      } yield assert(loadedFight)(isSome)).provideLayer(layers)
    },
    testM("Should update fight result and score") {
      val context = EmbeddedMongoDb.context
      import context._
      (for {
        _ <- FightUpdateOperations[LIO].addFight(fight)
        _ <- FightUpdateOperations[LIO].updateFightScoresAndResult(competitionId)(fightId, scores, fightResult)
        loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(categoryId, fightId)
        _           <- FightUpdateOperations[LIO].removeFightsForCompetition(competitionId)
      } yield assert(loadedFight)(isSome) && assert(loadedFight.get.scores)(isNonEmpty) &&
        assert(loadedFight.get.scores.size)(equalTo(2)) && assert(loadedFight.get.fightResult)(isSome) &&
        assert(loadedFight.get.fightResult.get.reason)(equalTo(fightResult.reason)) &&
        assert(loadedFight.get.fightResult.get.winnerId)(equalTo(fightResult.winnerId)) &&
        assert(loadedFight.get.fightResult.get.resultTypeId)(equalTo(fightResult.resultTypeId))
        )
        .provideLayer(layers)
    }
  ) @@ sequential @@ aroundAll(ZIO.effect(startEmbeddedMongo()))(server => URIO(stopServer()))
}
