package compman.compsrv.model

import compman.compsrv.model.dto.brackets.{StageDescriptorDTO, StageRoundType}
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.{MatIdAndSomeId, ScheduleDTO, ScheduleEntryDTO, ScheduleRequirementDTO}

package object extensions {
  import cats.implicits._

  final implicit class CompetitionPropertiesOps(private val c: CompetitionPropertiesDTO) extends AnyVal {
    def applyProperties(props: CompetitionPropertiesDTO): CompetitionPropertiesDTO = {
      for {
        pr <- Option(props)
        bracketsPublished         = Option(pr.getBracketsPublished).getOrElse(c.getBracketsPublished)
        startDate                 = Option(pr.getStartDate).getOrElse(c.getStartDate)
        endDate                   = Option(pr.getEndDate).getOrElse(c.getEndDate)
        emailNotificationsEnabled = Option(pr.getEmailNotificationsEnabled).getOrElse(c.getEmailNotificationsEnabled)
        competitionName           = Option(pr.getCompetitionName).getOrElse(c.getCompetitionName)
        emailTemplate             = Option(pr.getEmailTemplate).getOrElse(c.getEmailTemplate)
        schedulePublished         = Option(pr.getSchedulePublished).getOrElse(c.getSchedulePublished)
        timeZone                  = Option(pr.getTimeZone).getOrElse(c.getTimeZone)
      } yield c.setBracketsPublished(bracketsPublished).setStartDate(startDate).setEndDate(endDate)
        .setEmailNotificationsEnabled(emailNotificationsEnabled).setCompetitionName(competitionName)
        .setEmailTemplate(emailTemplate).setSchedulePublished(schedulePublished).setTimeZone(timeZone)

    }.getOrElse(c)
  }

  final implicit class CategoryRestrictionOps(private val c: CategoryRestrictionDTO) extends AnyVal {
    def aliasOrName: String = Option(c.getAlias).getOrElse(c.getName)
  }

  final implicit class SchedReqOps(private val s: ScheduleRequirementDTO) extends AnyVal {
    def categories: Option[Array[String]] = Option(s.getCategoryIds).orElse(Option(Array.empty))

    def categoriesOrEmpty: Array[String] = categories.getOrElse(Array.empty)

    def fightIds: Option[Array[String]] = Option(s.getFightIds).orElse(Option(Array.empty))

    def fightIdsOrEmpty: Array[String] = fightIds.getOrElse(Array.empty)
  }

  final implicit class SchedEntryOps(private val s: ScheduleEntryDTO) extends AnyVal {
    def categories: Option[Array[String]]       = Option(s.getCategoryIds)
    def categoriesOrEmpty: Array[String]        = categories.getOrElse(Array.empty)
    def fightIds: Option[Array[MatIdAndSomeId]] = Option(s.getFightIds)
    def fightIdsOrEmpty: Array[MatIdAndSomeId]  = fightIds.getOrElse(Array.empty)
    def requirementIds: Option[Array[String]]   = Option(s.getRequirementIds)
    def requirementIdsOrEmpty: Array[String]    = requirementIds.getOrElse(Array.empty)
    def addCategoryIds(categoryIds: IterableOnce[String]): ScheduleEntryDTO = s
      .setCategoryIds(s.categoriesOrEmpty ++ categoryIds)
    def addCategoryId(categoryId: String): ScheduleEntryDTO = s.setCategoryIds(s.categoriesOrEmpty :+ categoryId)
  }

  final implicit class CompetitorOps(private val c: CompetitorDTO) extends AnyVal {
    def competitorId: Option[String] = if (c.isPlaceholder) None else Option(c.getId)

    def placeholderId: Option[String] = if (c.isPlaceholder) Option(c.getId) else Option(s"placeholder-${c.getId}")

    def copy(categories: Array[String] = c.getCategories) = new CompetitorDTO(
      c.getId,
      c.getEmail,
      c.getUserId,
      c.getFirstName,
      c.getLastName,
      c.getBirthDate,
      c.getAcademy,
      categories,
      c.getCompetitionId,
      c.getRegistrationStatus,
      c.isPlaceholder,
      c.getPromo
    )
  }

  final implicit class ScheduleOps(private val c: ScheduleDTO) extends AnyVal {
    def mats: Map[String, MatDescriptionDTO] = Option(c.getMats).map(_.groupMapReduce(_.getId)(identity)((a, _) => a))
      .getOrElse(Map.empty)
  }

  final implicit class CompScoreOps(private val c: CompScoreDTO) extends AnyVal {
    def hasCompetitorIdOrPlaceholderId: Boolean = c.getCompetitorId != null || c.getPlaceholderId != null
  }

  final implicit class StageDescrOps(private val s: StageDescriptorDTO) extends AnyVal {
    def groupsNumber: Int = Option(s.getGroupDescriptors).map(_.length).getOrElse(0)
    def copy() = new StageDescriptorDTO(
      s.getId,
      s.getName,
      s.getCategoryId,
      s.getCompetitionId,
      s.getBracketType,
      s.getStageType,
      s.getStageStatus,
      s.getStageResultDescriptor,
      s.getInputDescriptor,
      s.getStageOrder,
      s.getWaitForPrevious,
      s.getHasThirdPlaceFight,
      s.getGroupDescriptors,
      s.getNumberOfFights,
      s.getFightDuration
    )
  }

  final implicit class CategoryDescriptorOps(private val c: CategoryDescriptorDTO) extends AnyVal {
    def copy() = new CategoryDescriptorDTO(c.getRestrictions, c.getId, c.getName, c.getRegistrationOpen)
  }

  final implicit class FightDescrOps(private val f: FightDescriptionDTO) extends AnyVal {
    def copy(
      id: String = f.getId,
      fightName: String = f.getFightName,
      roundType: StageRoundType = f.getRoundType,
      winFight: String = f.getWinFight,
      loseFight: String = f.getLoseFight,
      scores: Array[CompScoreDTO] = f.getScores,
      fightResult: FightResultDTO = f.getFightResult
    ): FightDescriptionDTO = {
      new FightDescriptionDTO(
        f.getId,
        f.getCategoryId,
        f.getFightName,
        f.getWinFight,
        f.getLoseFight,
        f.getScores,
        f.getDuration,
        f.getRound,
        f.getInvalid,
        f.getRoundType,
        f.getStatus,
        f.getFightResult,
        f.getMat,
        f.getNumberOnMat,
        f.getPriority,
        f.getCompetitionId,
        f.getPeriod,
        f.getStartTime,
        f.getStageId,
        f.getGroupId,
        f.getScheduleEntryId,
        f.getNumberInRound
      ).setId(id).setWinFight(winFight).setScores(scores).setLoseFight(loseFight).setRoundType(roundType)
        .setFightName(fightName).setFightResult(fightResult)
    }

    def competitors: List[String] = scores.map(_.toList.mapFilter(s => Option(s.getCompetitorId))).getOrElse(List.empty)

    def placeholders: List[String] = scores.map(_.toList.mapFilter(s => Option(s.getPlaceholderId)))
      .getOrElse(List.empty)

    def hasPlaceholder(placeholderId: String): Boolean = scores.exists(_.exists(_.getPlaceholderId == placeholderId))

    def scoresSize: Int                    = scores.map(_.length).getOrElse(0)
    def scoresOrEmpty: Array[CompScoreDTO] = scores.getOrElse(Array.empty)

    def round: Option[Int] = Option(f.getRound).map(_.toInt)

    def roundOrZero: Int = Option(f.getRound).map(_.toInt).getOrElse(0)

    def roundType: Option[StageRoundType] = Option(f.getRoundType)

    def containsFighter(cid: String): Boolean = scores.exists(_.exists(_.getCompetitorId == cid))

    def winnerId: Option[String] = Option(f.getFightResult).flatMap(res => Option(res.getWinnerId))

    def loserId: Option[String] = for {
      res        <- Option(f.getFightResult)
      scores     <- Option(f.getScores)
      loserScore <- scores.find(_.getCompetitorId != res.getWinnerId)
      id = loserScore.getCompetitorId
    } yield id

    def scores: Option[Array[CompScoreDTO]] = Option(f.getScores)

    private def putCompetitorIdAt(ind: Int, competitorId: String, sc: Array[CompScoreDTO]) = {
      for { updated <- if (ind >= 0 && ind < sc.length) Some(sc(ind).setCompetitorId(competitorId)) else None } yield (
        sc.slice(0, ind) :+ updated
      ) ++ sc.slice(ind + 1, sc.length)
    }

    private def putCompetitorWhere(
      competitorId: String,
      predicate: CompScoreDTO => Boolean
    ): Option[FightDescriptionDTO] = {
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
