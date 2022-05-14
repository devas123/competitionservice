package compman.compsrv.model

import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.Utils
import compservice.model.protobuf.model._

import java.time.Instant
import java.util.Date

package object extensions {
  import cats.implicits._

  final implicit class CompetitionPropertiesOps(private val c: CompetitionProperties) extends AnyVal {
    def applyProperties(props: CompetitionProperties): CompetitionProperties = {
      for {
        pr <- Option(props)
        bracketsPublished         = Option(pr.bracketsPublished).getOrElse(c.bracketsPublished)
        startDate                 = pr.startDate.orElse(c.startDate)
        endDate                   = pr.endDate.orElse(c.endDate)
        emailNotificationsEnabled = Option(pr.emailNotificationsEnabled).getOrElse(c.emailNotificationsEnabled)
        competitionName           = Option(pr.competitionName).getOrElse(c.competitionName)
        emailTemplate             = pr.emailTemplate.orElse(c.emailTemplate)
        schedulePublished         = Option(pr.schedulePublished).getOrElse(c.schedulePublished)
        timeZone                  = Option(pr.timeZone).getOrElse(c.timeZone)
      } yield c.update(
        _.bracketsPublished := bracketsPublished,
        _.startDate.setIfDefined(startDate),
        _.endDate.setIfDefined(endDate),
        _.emailNotificationsEnabled := emailNotificationsEnabled,
        _.competitionName := competitionName,
        _.emailTemplate.setIfDefined(emailTemplate),
        _.schedulePublished := schedulePublished,
        _.timeZone := timeZone,
      )

    }.getOrElse(c)
  }

  final implicit class CategoryRestrictionOps(private val c: CategoryRestriction) extends AnyVal {
    def aliasOrName: String = c.alias.getOrElse(c.name)
  }

  final implicit class InstantOps(i: Instant) {
    def asTimestamp: Timestamp = Timestamp.fromJavaProto(Timestamps.fromDate(Date.from(i)))
  }

  final implicit class SchedReqOps(private val s: ScheduleRequirement) extends AnyVal {
    def categories: Option[Array[String]] = Option(s.categoryIds).map(_.toArray).orElse(Option(Array.empty))

    def categoriesOrEmpty: Array[String] = s.categoryIds.toArray

    def fightIds: Option[Array[String]] = Option(s.fightIds).map(_.toArray).orElse(Option(Array.empty))

    def fightIdsOrEmpty: Array[String] = fightIds.getOrElse(Array.empty)
  }

  final implicit class SchedEntryOps(private val s: ScheduleEntry) extends AnyVal {
    def categories: Option[Array[String]]       = Option(s.categoryIds).map(_.toArray)
    def categoriesOrEmpty: Array[String]        = categories.getOrElse(Array.empty)
    def fightIds: Option[Array[StartTimeInfo]] = Option(s.fightScheduleInfo).map(_.toArray)
    def fightIdsOrEmpty: Array[StartTimeInfo]  = fightIds.getOrElse(Array.empty)
    def requirementIds: Option[Array[String]]   = Option(s.requirementIds).map(_.toArray)
    def requirementIdsOrEmpty: Array[String]    = requirementIds.getOrElse(Array.empty)
    def addCategoryIds(categoryIds: IterableOnce[String]): ScheduleEntry = s.addAllCategoryIds(categoryIds.iterator.toList)
    def addCategoryId(categoryId: String): ScheduleEntry = s.addCategoryIds(categoryId)
  }

  final implicit class CompetitorOps(private val c: Competitor) extends AnyVal {
    def competitorId: Option[String] = if (c.placeholder) None else Option(c.id)

    def placeholderId: Option[String] = if (c.placeholder) Option(c.id) else Option(s"placeholder-${c.id}")

    def copy(categories: Seq[String] = c.categories): Competitor = c.update(
      _.categories := categories
    )
  }

  final implicit class ScheduleOps(private val c: Schedule) extends AnyVal {
    def mats: Map[String, MatDescription] = Option(c.mats).map(ms => Utils.groupById(ms)(_.id))
      .getOrElse(Map.empty)
  }

  final implicit class CompScoreOps(private val c: CompScore) extends AnyVal {
    def hasCompetitorIdOrPlaceholderId: Boolean = c.competitorId.isDefined || c.placeholderId.isDefined
  }

  final implicit class StageDescrOps(private val s: StageDescriptor) extends AnyVal {
    def groupsNumber: Int = Option(s.groupDescriptors).map(_.length).getOrElse(0)
  }


  final implicit class FightDescrOps(private val f: FightDescription) extends AnyVal {
    def competitors: List[String] = scores.map(_.toList.mapFilter(s => s.competitorId)).getOrElse(List.empty)

    def placeholders: List[String] = scores.map(_.toList.mapFilter(s => Option(s.getPlaceholderId)))
      .getOrElse(List.empty)

    def hasPlaceholder(placeholderId: String): Boolean = scores.exists(_.exists(_.getPlaceholderId == placeholderId))

    def scoresSize: Int                    = scores.map(_.length).getOrElse(0)
    def scoresOrEmpty: Array[CompScore] = scores.getOrElse(Array.empty)

    def round: Option[Int] = Option(f.round)

    def roundOrZero: Int = Option(f.round).getOrElse(0)

    def roundType: Option[StageRoundType] = Option(f.roundType)

    def containsFighter(cid: String): Boolean = scores.exists(_.exists(_.getCompetitorId == cid))

    def winnerId: Option[String] = Option(f.getFightResult).flatMap(res => Option(res.getWinnerId))

    def loserId: Option[String] = for {
      res        <- f.fightResult
      winnerId <- res.winnerId
      scores     <- Option(f.scores)
      loserScore <- scores.find(_.competitorId.contains(winnerId))
      id <- loserScore.competitorId
    } yield id

    def scores: Option[Array[CompScore]] = Option(f.scores).map(_.toArray)

    private def putCompetitorIdAt(ind: Int, competitorId: String, sc: Array[CompScore]) = {
      for { updated <- if (ind >= 0 && ind < sc.length) Some(sc(ind).withCompetitorId(competitorId)) else None } yield (
        sc.slice(0, ind) :+ updated
      ) ++ sc.slice(ind + 1, sc.length)
    }

    private def putCompetitorWhere(
      competitorId: String,
      predicate: CompScore => Boolean
    ): Option[FightDescription] = {
      for {
        sc <- scores
        ind = sc.indexWhere(predicate)
        updated <- putCompetitorIdAt(ind, competitorId, sc)
        res = f.copy(scores = updated)
      } yield res
    }

    def pushCompetitorToPlaceholder(competitorId: String, placeholderId: String): Option[FightDescription] = {
      putCompetitorWhere(competitorId, _.placeholderId.contains(placeholderId))
    }

    def pushCompetitor(competitorId: String): Option[FightDescription] = {
      putCompetitorWhere(competitorId, _.competitorId.isEmpty)
    }
  }
}
