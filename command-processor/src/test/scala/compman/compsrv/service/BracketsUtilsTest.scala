package compman.compsrv.service

import cats.effect.IO
import compman.compsrv.{SpecBase, Utils}
import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.logic.fight.{BracketsUtils, FightStatusUtils, FightUtils}

class BracketsUtilsTest extends SpecBase with TestEntities {

  test("Should generate brackets for 8 fighters") {
    val compsSize = 8
    for {
      fights <- BracketsUtils
        .generateEmptyWinnerRoundsForCategory[IO](competitionId, categoryId, stageId, compsSize, 10)
      fighters <- IO(CompetitorService.generateRandomCompetitorsForCategory(
        size = compsSize,
        categoryId = categoryId,
        competitionId = competitionId
      ))
      res         = fights.fold(_ => List.empty, identity)
      distributed = BracketsUtils.distributeCompetitors(fighters, Utils.groupById(res)(_.id))
      distribRes  = distributed.fold(Map.empty, identity)
      _ <- IO {
        assert(res.size == 7)
        assert(distribRes.size == 7)
        assert(distribRes.values.filter(_.round == 0).forall(f =>
          f.scores.nonEmpty && f.scores.forall(_.competitorId.isDefined)
        ))
        assert(distribRes.values.filter(f => !f.scores.exists(_.competitorId.isEmpty)).forall(f =>
          f.scores.map(_.getCompetitorId).toSet.size == 2
        ))
      }
    } yield ()
  }
  test("Should generate brackets for 10 fighters") {
    val compsSize = 10
    for {
      fights <- BracketsUtils
        .generateEmptyWinnerRoundsForCategory[IO](competitionId, categoryId, stageId, compsSize, 10)
      fighters = CompetitorService
        .generateRandomCompetitorsForCategory(size = compsSize, categoryId = categoryId, competitionId = competitionId)
      res         = fights.fold(_ => List.empty, identity)
      distributed = BracketsUtils.distributeCompetitors(fighters, Utils.groupById(res)(_.id))
      distribRes  = distributed.fold(Map.empty, identity)
      _ <- IO {
        assert(res.size == 15)
        assert(distribRes.size == 15)
        assert(distribRes.values.count(_.round == 0) == 8)
        assert(distribRes.values.filter(f => !f.scores.exists(_.competitorId.isEmpty)).forall(f =>
          f.scores.map(_.getCompetitorId).toSet.size == 2
        ))
      }
    } yield ()
  }

  test("should process uncompletable fights and advance competitors") {
    val compsSize = 10
    for {
      fights <- BracketsUtils
        .generateEmptyWinnerRoundsForCategory[IO](competitionId, categoryId, stageId, compsSize, 600)
      fighters = CompetitorService
        .generateRandomCompetitorsForCategory(size = compsSize, categoryId = categoryId, competitionId = competitionId)
      res         = fights.fold(_ => List.empty, identity)
      distributed = BracketsUtils.distributeCompetitors(fighters, Utils.groupById(res)(_.id))
      distribRes  = distributed.fold(Map.empty, identity)
      marked <- FightUtils.markAndProcessUncompletableFights[IO](distribRes)
      _ <- IO {
        assert(marked.size == 15)
        assert(marked.count(_._2.round == 0) == 8)
        assert(marked.count(e => e._2.round == 0 && FightStatusUtils.isUncompletable(e._2)) == 6)
        assert(marked.count(e => e._2.round != 0 && FightStatusUtils.isUncompletable(e._2)) == 0)
        assert(marked.count(e => e._2.round == 1 && e._2.scores.count(_.competitorId.isDefined) == 2) >= 2)
        assert(marked.count(_._2.round == 1) == 4)
        assert(marked.count(_._2.round == 2) == 2)
        assert(marked.count(_._2.round == 3) == 1)
      }
    } yield ()
  }

}
