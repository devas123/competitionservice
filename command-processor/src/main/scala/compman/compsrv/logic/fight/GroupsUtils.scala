package compman.compsrv.logic.fight

import cats.implicits._
import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic._
import compman.compsrv.model.Errors
import compman.compsrv.model.extensions._
import compservice.model.protobuf.model._

import scala.collection.{mutable, SortedSet}

object GroupsUtils {

  def createGroupFights(
    competitionId: String,
    categoryId: String,
    stageId: String,
    groupId: String,
    durationSeconds: Int,
    competitors: List[Competitor]
  ): List[FightDescription] = {
    val combined = createPairs(competitors)
    combined.filter { it => it._1.id != it._2.id }.distinctBy { it => SortedSet(it._1.id, it._2.id).mkString }
      .zipWithIndex.map { e =>
        val (comps, ind) = e
        fightDescription(
          competitionId = competitionId,
          categoryId = categoryId,
          stageId = stageId,
          round = 0,
          roundType = StageRoundType.GROUP,
          numberInRound = ind,
          durationSeconds = durationSeconds,
          fightName = s"Round 0 fight $ind",
          groupId = Option(groupId)
        ).withScores(Seq(
          createCompscoreForGroup(comps._1.competitorId, comps._1.placeholderId, 0),
          createCompscoreForGroup(comps._2.competitorId, comps._2.placeholderId, 1)
        ))
      }
  }

  def validateFights(fights: List[FightDescription]): CanFail[List[FightDescription]] = {
    for {
      _ <-
        assertE(fights.forall { it => it.scoresSize == 2 }, Some("Some fights do not have scores. Something is wrong."))
      _ <- assertE(
        fights.forall { it => it.scores.forall(_.hasCompetitorIdOrPlaceholderId) },
        Some("Not all fights have placeholders or real competitors assigned.")
      )
    } yield fights
  }

  def distributeCompetitors(
    competitors: List[Competitor],
    rawFights: List[FightDescription]
  ): CanFail[List[FightDescription]] = {
    for {
      fights <- validateFights(rawFights)
      placeholders = fights.flatMap { f => f.placeholders }.distinct
      fightsWithDistributedCompetitors <- competitors.zip(placeholders).traverse { pair =>
        val (competitor, placeholderId) = pair
        for {
          fight <- fights.find(f => f.hasPlaceholder(placeholderId))
            .toRight(Errors.InternalError(Some(s"Cannot find fight for placeholder $placeholderId")))
          updated <- fight.pushCompetitorToPlaceholder(competitor.id, placeholderId).toRight(Errors.InternalError(Some(
            s"Cannot add competitor ${competitor.id} to placeholder $placeholderId"
          )))
        } yield updated
      }
      res <- validateFights(fightsWithDistributedCompetitors)
    } yield res
  }

  def generateStageFights[F[+_]: Monad](
    competitionId: String,
    categoryId: String,
    stage: StageDescriptor,
    durationSeconds: Int,
    competitors: List[Competitor]
  ): F[CanFail[List[FightDescription]]] = {
    (for {
      _ <- assertET[F](stage.groupsNumber > 0, Some(s"Group descriptors are empty (${stage.groupsNumber})"))
      comps: List[Competitor] = stage.stageOrder match {
        case 0 => competitors
        case _ =>
          if (stage.getInputDescriptor.numberOfCompetitors <= competitors.size) {
            competitors.take(stage.getInputDescriptor.numberOfCompetitors)
          } else {
            competitors ++
              generatePlaceholderCompetitorsForGroup(stage.getInputDescriptor.numberOfCompetitors - competitors.size)
          }
      }
      totalCapacity = stage.groupDescriptors.map(_.size).sum
      _ <- assertET[F](
        totalCapacity == comps.size,
        Some(s"Total groups capacity ($totalCapacity) does not match the competitors (${comps.size}) size")
      )
      fights = stage.groupDescriptors.foldLeft(0 -> List.empty[FightDescription]) { (acc, groupDescriptor) =>
        (acc._1 + groupDescriptor.size) ->
          (acc._2 ++ createGroupFights(
            competitionId,
            categoryId,
            stage.id,
            groupDescriptor.id,
            durationSeconds,
            comps.slice(acc._1, acc._1 + groupDescriptor.size)
          ))
      }._2
      res <- EitherT.fromEither[F](validateFights(fights))
    } yield res).value
  }

  case class CompetitorGroupPoints(points: Int, additionalPoints: Int, groupId: String)

  def buildStageResults(
    stageStatus: StageStatus,
    fights: List[FightDescription],
    stageId: String,
    fightResultOptions: List[FightResultOption]
  ): CanFail[List[CompetitorStageResult]] = {
    stageStatus match {
      case StageStatus.FINISHED =>
        val competitorPointsMap = mutable.Map.empty[String, CompetitorGroupPoints]
        fights.foreach { fight =>
          val pointsDescriptor = fightResultOptions.find { p => p.id == fight.getFightResult.getResultTypeId }
          if (pointsDescriptor.exists(_.draw)) {
            fight.competitors.foreach { it =>
              updateCompetitorPointsMapForWinner(competitorPointsMap, pointsDescriptor, fight.getGroupId, it)
            }
          } else {
            fight.winnerId.map { it =>
              updateCompetitorPointsMapForWinner(competitorPointsMap, pointsDescriptor, fight.getGroupId, it)
            }
            fight.loserId.map { it =>
              competitorPointsMap.updateWith(it) { u =>
                val basis = u.getOrElse(CompetitorGroupPoints(0, 0, fight.getGroupId))
                Some(CompetitorGroupPoints(
                  pointsDescriptor.map(_.loserPoints).getOrElse(0) + basis.points,
                  pointsDescriptor.flatMap(_.loserAdditionalPoints).getOrElse(0) + basis.additionalPoints,
                  basis.groupId
                ))
              }
            }
          }
        }
        Right(
          competitorPointsMap.groupBy(_._2.groupId).values.toList.flatMap(cpm =>
            cpm.toList.sortBy { pair => pair._2.points * 10000 + pair._2.additionalPoints }.reverse
              .zipWithIndex.map { case ((key, competitorGroupPoints), i) =>
              CompetitorStageResult().withRound(0).withGroupId(competitorGroupPoints.groupId).withCompetitorId(key)
                .withPoints(competitorGroupPoints.points).withPlace(i + 1).withStageId(stageId)
            }
          )
        )
      case _ => Left(Errors.InternalError("Stage is not finished."))
    }
  }

  private def updateCompetitorPointsMapForWinner(
    competitorPointsMap: mutable.Map[String, CompetitorGroupPoints],
    pointsDescriptor: Option[FightResultOption],
    groupId: String,
    it: String
  ) = {
    competitorPointsMap.updateWith(it) { u =>
      val basis = u.getOrElse(CompetitorGroupPoints(0, 0, groupId))
      Some(CompetitorGroupPoints(
        pointsDescriptor.map(_.winnerPoints).getOrElse(0) + basis.points,
        pointsDescriptor.flatMap(_.winnerAdditionalPoints).getOrElse(0) + basis.additionalPoints,
        basis.groupId
      ))
    }
  }
}
