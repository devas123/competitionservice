package compman.compsrv.service

import cats.Eval
import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.logic.fight.GroupsUtils
import compman.compsrv.SpecBase

class GroupUtilsTest extends SpecBase with TestEntities {
  test("Should generate brackets for 8 fighters") {
      val fighters = CompetitorService.generateRandomCompetitorsForCategory(
        size = totalNumberOfCompetitors,
        categoryId = categoryId,
        competitionId = competitionId
      )
      val fights = GroupsUtils.generateStageFights[Eval](
        competitionId,
        categoryId,
        stageForGroupsGeneration,
        600,
        fighters
      ).value
      val unfolded = fights.fold(_ => List.empty, identity)
      val totalFights = groupRange.map(startingCompetitorsSizeForGroup + _).map(s => s * (s - 1)).sum / 2
      assert(fights.isRight)
        assert(unfolded.size == totalFights)
  }
}
