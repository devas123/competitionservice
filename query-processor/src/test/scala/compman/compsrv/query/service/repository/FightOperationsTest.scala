package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.{URIO, ZIO, ZLayer}
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{aroundAll, sequential}

class FightOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  import EmbeddedMongoDb._
  override def spec: ZSpec[Any, Throwable] = suite("Fight operations")(
    testM("Should save and load fight by id") {
      (for {
        _     <- FightUpdateOperations[LIO].addFight(fight)
        loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(categoryId, fightId)
      } yield assert(loadedFight)(isSome)).provideLayer(layers)
    },
    testM("Should update fight result and score") {
      (for {
        _     <- FightUpdateOperations[LIO].addFight(fight)
        _ <- FightUpdateOperations[LIO].updateFightScoresAndResult(competitionId)(fightId, scores, fightResult)
          loadedFight <- FightQueryOperations[LIO].getFightById(competitionId)(categoryId, fightId)
      } yield assert(loadedFight)(isSome) &&
        assert(loadedFight.get.scores)(isNonEmpty) &&
        assert(loadedFight.get.scores.size)(equalTo(2)) &&
        assert(loadedFight.get.fightResult)(isSome)
        ).provideLayer(layers)
    }
  ) @@ sequential @@ aroundAll(ZIO.effect(startEmbeddedMongo()))(server => URIO(stopServer(server._1)))
}
