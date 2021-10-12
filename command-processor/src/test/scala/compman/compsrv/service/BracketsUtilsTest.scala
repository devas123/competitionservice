package compman.compsrv.service

import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.logic.fights.{BracketsUtils, FightUtils}
import zio.{Task, URIO, ZIO}
import zio.interop.catz._
import zio.test._
import zio.test.Assertion._

object BracketsUtilsTest extends DefaultRunnableSpec with TestEntities {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("Brackets utils") {
    testM("Should generate brackets for 8 fighters") {
      val compsSize = 8
      for {
        fights <- BracketsUtils.generateEmptyWinnerRoundsForCategory[Task](
          competitionId,
          categoryId,
          stageId,
          compsSize,
          BigDecimal(10).bigDecimal
        )
        fighters <- ZIO.effect(CompetitorService.generateRandomCompetitorsForCategory(
          size = compsSize,
          categoryId = categoryId,
          competitionId = competitionId
        ))
        res         = fights.fold(_ => List.empty, identity)
        distributed = BracketsUtils.distributeCompetitors(fighters, res.groupMapReduce(_.getId)(identity)((a, _) => a))
        distribRes <- ZIO.effectTotal(distributed).flatMap(_.fold(err => ZIO.fail(err), ZIO.effectTotal(_)))
          .onError(err => URIO(println(err.toString)))
      } yield assert(res.size)(equalTo(7)) && assert(distribRes.size)(equalTo(7)) &&
        assert(distribRes.values.filter(_.getRound == 0).forall(f =>
          f.getScores != null && f.getScores.nonEmpty && f.getScores.forall(_.getCompetitorId != null)
        ))(isTrue) &&
        assert(distribRes.values.filter(f => f.getScores != null && !f.getScores.exists(_.getCompetitorId == null)).forall(f =>
          f.getScores.map(_.getCompetitorId).toSet.size == 2
        ))(isTrue)
    }
    testM("Should generate brackets for 10 fighters") {
      val compsSize = 10
      for {
        fights <- BracketsUtils.generateEmptyWinnerRoundsForCategory[Task](
          competitionId,
          categoryId,
          stageId,
          compsSize,
          BigDecimal(10).bigDecimal
        )
        fighters <- ZIO.effect(CompetitorService.generateRandomCompetitorsForCategory(
          size = compsSize,
          categoryId = categoryId,
          competitionId = competitionId
        ))
        res         = fights.fold(_ => List.empty, identity)
        distributed = BracketsUtils.distributeCompetitors(fighters, res.groupMapReduce(_.getId)(identity)((a, _) => a))
        distribRes <- ZIO.effectTotal(distributed).flatMap(_.fold(err => ZIO.fail(err), ZIO.effectTotal(_)))
          .onError(err => URIO(println(err.toString)))
      } yield assert(res.size)(equalTo(15)) && assert(distribRes.size)(equalTo(15)) &&
        assert(distribRes.values.count(_.getRound == 0))(equalTo(8)) &&
        assert(distribRes.values.filter(f => f.getScores != null && !f.getScores.exists(_.getCompetitorId == null)).forall(f =>
          f.getScores.map(_.getCompetitorId).toSet.size == 2
        ))(isTrue)
    }
    testM("should process uncompletable fights") {
      val compsSize = 10
      for {
        fights <- BracketsUtils.generateEmptyWinnerRoundsForCategory[Task](
          competitionId,
          categoryId,
          stageId,
          compsSize,
          BigDecimal(10).bigDecimal
        )
        fighters <- ZIO.effect(CompetitorService.generateRandomCompetitorsForCategory(
          size = compsSize,
          categoryId = categoryId,
          competitionId = competitionId
        ))
        res         = fights.fold(_ => List.empty, identity)
        distributed = BracketsUtils.distributeCompetitors(fighters, res.groupMapReduce(_.getId)(identity)((a, _) => a))
        distribRes <- ZIO.effectTotal(distributed).flatMap(_.fold(err => ZIO.fail(err), ZIO.effectTotal(_)))
          .onError(err => URIO(println(err.toString)))
        marked <- FightUtils.markAndProcessUncompletableFights[Task](distribRes)
      } yield assert(marked.size)(equalTo(15))
    }
  }
}
