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
        schedulePublished         = Option(pr.schedulePublished).getOrElse(c.schedulePublished)
        timeZone                  = Option(pr.timeZone).getOrElse(c.timeZone)
        status = Option(pr.status).getOrElse(c.status)
      } yield c.update(
        _.bracketsPublished := bracketsPublished,
        _.startDate.setIfDefined(startDate),
        _.endDate.setIfDefined(endDate),
        _.emailNotificationsEnabled := emailNotificationsEnabled,
        _.competitionName := competitionName,
        _.schedulePublished := schedulePublished,
        _.timeZone := timeZone,
        _.status := status
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
    def categories: Option[Seq[String]] = Option(s.categoryIds).map(_.toSeq).orElse(Option(Seq.empty))

    def categoriesOrEmpty: Seq[String] = s.categoryIds.toSeq

    def fightIds: Option[Seq[String]] = Option(s.fightIds).map(_.toSeq).orElse(Option(Seq.empty))

    def fightIdsOrEmpty: Seq[String] = fightIds.getOrElse(Seq.empty)
  }

  final implicit class SchedEntryOps(private val s: ScheduleEntry) extends AnyVal {
    def categories: Option[Seq[String]]       = Option(s.categoryIds).map(_.toSeq)
    def categoriesOrEmpty: Seq[String]        = categories.getOrElse(Seq.empty)
    def fightIds: Option[Seq[StartTimeInfo]] = Option(s.fightScheduleInfo).map(_.toSeq)
    def fightIdsOrEmpty: Seq[StartTimeInfo]  = fightIds.getOrElse(Seq.empty)
    def requirementIds: Option[Seq[String]]   = Option(s.requirementIds).map(_.toSeq)
    def requirementIdsOrEmpty: Seq[String]    = requirementIds.getOrElse(Seq.empty)
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
    def scoresOrEmpty: Seq[CompScore] = scores.getOrElse(Seq.empty)

    def round: Option[Int] = Option(f.round)

    def roundOrZero: Int = Option(f.round).getOrElse(0)

    def roundType: Option[StageRoundType] = Option(f.roundType)

    def containsFighter(cid: String): Boolean = scores.exists(_.exists(_.getCompetitorId == cid))

    def winnerId: Option[String] = Option(f.getFightResult).flatMap(res => Option(res.getWinnerId))

    def loserId: Option[String] = for {
      res        <- f.fightResult
      winnerId <- res.winnerId
      scores     = f.scores
      loserScore <- scores.find(s => !s.competitorId.contains(winnerId))
      id <- loserScore.competitorId
    } yield id

    def scores: Option[Seq[CompScore]] = Option(f.scores).map(_.toSeq)

    private def putCompetitorIdAt(ind: Int, competitorId: String, sc: Seq[CompScore]) = {
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
      putCompetitorWhere(competitorId, cs => !cs.competitorId.exists(_.nonEmpty))
    }
  }
}
