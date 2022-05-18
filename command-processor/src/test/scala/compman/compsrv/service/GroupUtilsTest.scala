package compman.compsrv.service

import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.logic.fight.GroupsUtils
import zio.{Task, ZIO}
import zio.interop.catz._
import zio.test._
import zio.test.TestAspect.sequential

object GroupUtilsTest extends DefaultRunnableSpec with TestEntities {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Group utils")(testM("Should generate brackets for 8 fighters") {
      for {
        fighters <- ZIO.effect(CompetitorService.generateRandomCompetitorsForCategory(
          size = totalNumberOfCompetitors,
          categoryId = categoryId,
          competitionId = competitionId
        ))
        fights <- GroupsUtils.generateStageFights[Task](
          competitionId,
          categoryId,
          stageForGroupsGeneration,
          600,
          fighters
        )
        unfolded = fights.fold(_ => List.empty, identity)
        totalFights = groupRange.map(startingCompetitorsSizeForGroup + _).map(s => s * (s - 1)).sum / 2
      } yield assertTrue(fights.isRight) &&
        assertTrue(unfolded.size == totalFights)
    }) @@ sequential
}
