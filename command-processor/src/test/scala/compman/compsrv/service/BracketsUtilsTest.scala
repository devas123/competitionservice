package compman.compsrv.service

import compman.compsrv.Utils
import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.logic.fight.{BracketsUtils, FightUtils}
import compservice.model.protobuf.model.FightStatus
import zio.{Task, URIO, ZIO}
import zio.interop.catz._
import zio.test._
import zio.test.TestAspect.sequential

object BracketsUtilsTest extends DefaultRunnableSpec with TestEntities {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("Brackets utils") (
    testM("Should generate brackets for 8 fighters") {
      val compsSize = 8
      for {
        fights <- BracketsUtils.generateEmptyWinnerRoundsForCategory[Task](
          competitionId,
          categoryId,
          stageId,
          compsSize,
          10
        )
        fighters <- ZIO.effect(CompetitorService.generateRandomCompetitorsForCategory(
          size = compsSize,
          categoryId = categoryId,
          competitionId = competitionId
        ))
        res         = fights.fold(_ => List.empty, identity)
        distributed = BracketsUtils.distributeCompetitors(fighters, Utils.groupById(res)(_.id))
        distribRes <- ZIO.fromEither(distributed)
          .onError(err => URIO(println(err.toString)))
      } yield assertTrue(res.size == 7) && assertTrue(distribRes.size == 7) &&
        assertTrue(distribRes.values.filter(_.round == 0).forall(f =>
          f.scores.nonEmpty && f.scores.forall(_.competitorId.isDefined)
        )) &&
        assertTrue(distribRes.values.filter(f => !f.scores.exists(_.competitorId.isEmpty)).forall(f =>
          f.scores.map(_.getCompetitorId).toSet.size == 2
        ))
    },
    testM("Should generate brackets for 10 fighters") {
      val compsSize = 10
      for {
        fights <- BracketsUtils.generateEmptyWinnerRoundsForCategory[Task](
          competitionId,
          categoryId,
          stageId,
          compsSize,
          10
        )
        fighters <- ZIO.effect(CompetitorService.generateRandomCompetitorsForCategory(
          size = compsSize,
          categoryId = categoryId,
          competitionId = competitionId
        ))
        res         = fights.fold(_ => List.empty, identity)
        distributed = BracketsUtils.distributeCompetitors(fighters, Utils.groupById(res)(_.id))
        distribRes <- ZIO.fromEither(distributed)
          .onError(err => URIO(println(err.toString)))
      } yield assertTrue(res.size == 15) && assertTrue(distribRes.size == 15) &&
        assertTrue(distribRes.values.count(_.round == 0) == 8) &&
        assertTrue(distribRes.values.filter(f => !f.scores.exists(_.competitorId.isEmpty)).forall(f =>
          f.scores.map(_.getCompetitorId).toSet.size == 2
        ))
    },

    testM("should process uncompletable fights and advance competitors") {
      val compsSize = 10
      for {
        fights <- BracketsUtils.generateEmptyWinnerRoundsForCategory[Task](
          competitionId,
          categoryId,
          stageId,
          compsSize,
          600
        )
        fighters <- ZIO.effect(CompetitorService.generateRandomCompetitorsForCategory(
          size = compsSize,
          categoryId = categoryId,
          competitionId = competitionId
        ))
        res         = fights.fold(_ => List.empty, identity)
        distributed = BracketsUtils.distributeCompetitors(fighters, Utils.groupById(res)(_.id))
        distribRes <- ZIO.fromEither(distributed)
          .onError(err => URIO(println(err.toString)))
        marked <- FightUtils.markAndProcessUncompletableFights[Task](distribRes)
      } yield assertTrue(marked.size == 15) &&
        assertTrue(marked.count(_._2.round == 0) == 8) &&
        assertTrue(marked.count(e => e._2.round == 0 && e._2.status == FightStatus.UNCOMPLETABLE) == 6) &&
        assertTrue(marked.count(e => e._2.round != 0 && e._2.status == FightStatus.UNCOMPLETABLE) == 0) &&
        assertTrue(marked.count(e => e._2.round == 1 && e._2.scores.count(_.competitorId.isDefined) == 2) >= 2) &&
        assertTrue(marked.count(_._2.round == 1) == 4) &&
        assertTrue(marked.count(_._2.round == 2) == 2) &&
        assertTrue(marked.count(_._2.round == 3) == 1)
    }
  ) @@ sequential
}
