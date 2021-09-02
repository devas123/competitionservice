package compman.compsrv.model

import compman.compsrv.model.dto.brackets.{StageDescriptorDTO, StageRoundType}
import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CategoryRestrictionDTO, CompScoreDTO, CompetitionPropertiesDTO, CompetitorDTO, FightDescriptionDTO, FightResultDTO}
import cats.implicits._
import compman.compsrv.model.dto.schedule.{MatIdAndSomeId, ScheduleEntryDTO, ScheduleRequirementDTO}

import java.time.Instant

package object extension {

  private def parseDate(date: Any, default: Option[Instant] = None) = if (date != null) {
    Some(Instant.ofEpochMilli(date.toString.toLong))
  } else {
    default
  }

  implicit class CompetitionPropertiesOps(c: CompetitionPropertiesDTO) {
    def applyProperties(props: Map[String, String]): CompetitionPropertiesDTO = {
      for {
        pr <- Option(props)
        bracketsPublished <- pr.get("bracketsPublished").map(_.toBoolean).orElse(Option(c.getBracketsPublished).map(_.booleanValue()))
        startDate <- parseDate(pr("startDate"), Option(c.getStartDate))
        endDate <- parseDate(pr("endDate"), Option(c.getEndDate))
        emailNotificationsEnabled <- pr.get("emailNotificationsEnabled").map(_.toBoolean).orElse(Option(c.getEmailNotificationsEnabled).map(_.booleanValue()))
        competitionName <- pr.get("competitionName").orElse(Option(c.getCompetitionName))
        emailTemplate <- pr.get("emailTemplate").orElse(Option(c.getEmailTemplate))
        schedulePublished <- pr.get("schedulePublished").map(_.toBoolean).orElse(Option(c.getSchedulePublished).map(_.booleanValue()))
        timeZone <- pr.get("timeZone").orElse(Option(c.getTimeZone))
      } yield c
        .setBracketsPublished(bracketsPublished)
        .setStartDate(startDate)
        .setEndDate(endDate)
        .setEmailNotificationsEnabled(emailNotificationsEnabled)
        .setCompetitionName(competitionName)
        .setEmailTemplate(emailTemplate)
        .setSchedulePublished(schedulePublished)
        .setTimeZone(timeZone)

    }.getOrElse(c)
  }

  implicit class CategoryRestrictionOps(c: CategoryRestrictionDTO) {
    def aliasOrName: String = Option(c.getAlias).getOrElse(c.getName)
  }

  implicit class SchedReqOps(s: ScheduleRequirementDTO) {
    def categories: Option[Array[String]] = Option(s.getCategoryIds)

    def categoriesOrEmpty: Array[String] = categories.getOrElse(Array.empty)

    def fightIds: Option[Array[String]] = Option(s.getFightIds)

    def fightIdsOrEmpty: Array[String] = fightIds.getOrElse(Array.empty)
  }

  implicit class SchedEntryOps(s: ScheduleEntryDTO) {
    def categories: Option[Array[String]] = Option(s.getCategoryIds)
    def categoriesOrEmpty: Array[String] = categories.getOrElse(Array.empty)
    def fightIds: Option[Array[MatIdAndSomeId]] = Option(s.getFightIds)
    def fightIdsOrEmpty: Array[MatIdAndSomeId] = fightIds.getOrElse(Array.empty)
    def requirementIds: Option[Array[String]] = Option(s.getRequirementIds)
    def requirementIdsOrEmpty: Array[String] = requirementIds.getOrElse(Array.empty)
    def addCategoryIds(categoryIds: IterableOnce[String]): ScheduleEntryDTO = s.setCategoryIds(s.categoriesOrEmpty ++ categoryIds)
    def addCategoryId(categoryId: String): ScheduleEntryDTO = s.setCategoryIds(s.categoriesOrEmpty :+ categoryId)
  }

  implicit class CompetitorOps(c: CompetitorDTO) {
    def competitorId: Option[String] = if (c.isPlaceholder) None else Option(c.getId)

    def placeholderId: Option[String] = if (c.isPlaceholder) Option(c.getId) else Option(s"placeholder-${c.getId}")
  }

  implicit class CompScoreOps(c: CompScoreDTO) {
    def hasCompetitorIdOrPlaceholderId: Boolean = c.getCompetitorId != null || c.getPlaceholderId != null
  }

  implicit class StageDescrOps(s: StageDescriptorDTO) {
    def groupsNumber: Int = Option(s.getGroupDescriptors).map(_.length).getOrElse(0)
  }


  implicit class FightDescrOps(f: FightDescriptionDTO) {
    def copy(
              fightName: String = f.getFightName,
              roundType: StageRoundType = f.getRoundType,
              winFight: String = f.getWinFight,
              loseFight: String = f.getLoseFight,
              scores: Array[CompScoreDTO] = f.getScores,
              fightResult: FightResultDTO = f.getFightResult
            ): FightDescriptionDTO = f
      .setWinFight(winFight)
      .setScores(scores)
      .setLoseFight(loseFight)
      .setRoundType(roundType)
      .setFightName(fightName)
      .setFightResult(fightResult)

    def competitors: List[String] = scores.map(_.toList.mapFilter(s => Option(s.getCompetitorId))).getOrElse(List.empty)

    def placeholders: List[String] = scores.map(_.toList.mapFilter(s => Option(s.getPlaceholderId))).getOrElse(List.empty)

    def hasPlaceholder(placeholderId: String): Boolean = scores.exists(_.exists(_.getPlaceholderId == placeholderId))

    def scoresSize: Int = scores.map(_.length).getOrElse(0)
    def scoresOrEmpty: Array[CompScoreDTO] = scores.getOrElse(Array.empty)

    def round: Option[Int] = Option(f.getRound).map(_.toInt)

    def roundOrZero: Int = Option(f.getRound).map(_.toInt).getOrElse(0)

    def roundType: Option[StageRoundType] = Option(f.getRoundType)

    def containsFighter(cid: String): Boolean = scores.exists(_.exists(_.getCompetitorId == cid))

    def winnerId: Option[String] = Option(f.getFightResult).flatMap(res => Option(res.getWinnerId))

    def loserId: Option[String] =
      for {
        res <- Option(f.getFightResult)
        scores <- Option(f.getScores)
        loserScore <- scores.find(_.getCompetitorId != res.getWinnerId)
        id = loserScore.getCompetitorId
      } yield id

    def scores: Option[Array[CompScoreDTO]] = Option(f.getScores)

    private def putCompetitorIdAt(ind: Int, competitorId: String, sc: Array[CompScoreDTO]) = {
      for {
        updated <- if (ind >= 0 && ind < sc.length) Some(sc(ind).setCompetitorId(competitorId)) else None
      } yield (sc.slice(0, ind) :+ updated) ++ sc.slice(ind + 1, sc.length)
    }

    private def putCompetitorWhere(competitorId: String, predicate: CompScoreDTO => Boolean): Option[FightDescriptionDTO] = {
      for {
        sc <- scores
        ind = sc.indexWhere(predicate)
        updated <- putCompetitorIdAt(ind, competitorId, sc)
        res = copy(scores = updated)
      } yield res
    }

    def pushCompetitorToPlaceholder(competitorId: String, placeholderId: String): Option[FightDescriptionDTO] = {
      putCompetitorWhere(competitorId, _.getPlaceholderId == placeholderId)
    }

    def pushCompetitor(competitorId: String): Option[FightDescriptionDTO] = {
      putCompetitorWhere(competitorId, _.getCompetitorId == null)
    }
  }
}
