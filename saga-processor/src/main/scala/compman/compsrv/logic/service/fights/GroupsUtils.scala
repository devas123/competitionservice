package compman.compsrv.logic.service.fights

import cats.implicits._
import cats.Monad
import cats.data.EitherT
import compman.compsrv.model.Errors
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CompetitorDTO, FightDescriptionDTO}
import compman.compsrv.model.extension._

import scala.collection.{mutable, SortedSet}

object GroupsUtils {

  def createGroupFights(
    competitionId: String,
    categoryId: String,
    stageId: String,
    groupId: String,
    duration: BigDecimal,
    competitors: List[CompetitorDTO]
  ): List[FightDescriptionDTO] = {
    val combined = createPairs(competitors)
    combined.filter { it => it._1.getId != it._2.getId }.distinctBy { it =>
      SortedSet(it._1.getId, it._2.getId).mkString
    }.zipWithIndex.map { e =>
      val (comps, ind) = e
      fightDescription(
        competitionId = competitionId,
        categoryId = categoryId,
        stageId = stageId,
        round = 0,
        roundType = StageRoundType.GROUP,
        numberInRound = ind,
        duration = duration,
        fightName = "Round 0 fight $ind",
        groupId = groupId
      ).setScores(Array(
        createCompscore(comps._2.competitorId, comps._1.placeholderId, 0),
        createCompscore(comps._1.competitorId, comps._2.placeholderId, 1)
      ))
    }
  }

  def validateFights(fights: List[FightDescriptionDTO]): CanFail[List[FightDescriptionDTO]] = {
    for {
      _ <-
        assertE(fights.forall { it => it.scoresSize == 2 }, Some("Some fights do not have scores. Something is wrong."))
      _ <- assertE(
        fights.forall { it => it.scores.exists(_.forall(_.hasCompetitorIdOrPlaceholderId)) },
        Some("Not all fights have placeholders or real competitors assigned.")
      )
    } yield fights
  }

  def distributeCompetitors(
    competitors: List[CompetitorDTO],
    rawFights: List[FightDescriptionDTO]
  ): CanFail[List[FightDescriptionDTO]] = {
    for {
      fights <- validateFights(rawFights)
      placeholders = fights.flatMap { f => f.placeholders }.distinct
      fightsWithDistributedCompetitors <- competitors.zip(placeholders).traverse { pair =>
        val (competitor, placeholderId) = pair
        for {
          fight <- fights.find(f => f.hasPlaceholder(placeholderId))
            .toRight(Errors.InternalError(Some(s"Cannot find fight for placeholder $placeholderId")))
          updated <- fight.pushCompetitorToPlaceholder(competitor.getId, placeholderId).toRight(Errors.InternalError(
            Some(s"Cannot add competitor ${competitor.getId} to placeholder $placeholderId")
          ))
        } yield updated
      }
      res <- validateFights(fightsWithDistributedCompetitors)
    } yield res
  }

  def generateStageFights[F[+_]: Monad](
    competitionId: String,
    categoryId: String,
    stage: StageDescriptorDTO,
    duration: BigDecimal,
    competitors: List[CompetitorDTO]
  ): F[CanFail[List[FightDescriptionDTO]]] = {
    (for {
      _ <- assertET[F](stage.groupsNumber > 0, Some(s"Group descriptors are empty (${stage.groupsNumber})"))
      comps: List[CompetitorDTO] = stage.getStageOrder.toInt match {
        case 0 => competitors
        case _ =>
          if (stage.getInputDescriptor.getNumberOfCompetitors <= competitors.size) {
            competitors.take(stage.getInputDescriptor.getNumberOfCompetitors)
          } else {
            competitors ++
              generatePlaceholderCompetitorsForGroup(stage.getInputDescriptor.getNumberOfCompetitors - competitors.size)
          }
      }
      totalCapacity = stage.getGroupDescriptors.map(_.getSize.toInt).sum
      _ <- assertET[F](
        totalCapacity == comps.size,
        Some(s"Total groups capacity ($totalCapacity) does not match the competitors (${comps.size}) size")
      )
      fights = stage.getGroupDescriptors.foldLeft(0 -> List.empty[FightDescriptionDTO]) { (acc, groupDescriptorDTO) =>
        (acc._1 + groupDescriptorDTO.getSize) ->
          (acc._2 ++ createGroupFights(
            competitionId,
            categoryId,
            stage.getId,
            groupDescriptorDTO.getId,
            duration,
            comps.slice(acc._1, acc._1 + groupDescriptorDTO.getSize)
          ))
      }._2
      res <- EitherT.fromEither[F](validateFights(fights))
    } yield res).value
  }

  def buildStageResults(
    stageStatus: StageStatus,
    fights: List[FightDescriptionDTO],
    stageId: String,
    fightResultOptions: List[FightResultOptionDTO]
  ): CanFail[List[CompetitorStageResultDTO]] = {
    stageStatus match {
      case StageStatus.FINISHED =>
        val competitorPointsMap = mutable.Map.empty[String, (Int, Int, String)]
        fights.foreach { fight =>
          val pointsDescriptor = fightResultOptions.find { p => p.getId == fight.getFightResult.getResultTypeId }
          pointsDescriptor.map(_.isDraw) match {
            case Some(true) => fight.competitors.foreach { it =>
                competitorPointsMap.updateWith(it) { u =>
                  val basis = u.getOrElse((0, 0, fight.getGroupId))
                  Some((
                    pointsDescriptor.map(_.getWinnerPoints.toInt).getOrElse(0) + basis._1,
                    pointsDescriptor.map(_.getWinnerAdditionalPoints.toInt).getOrElse(0) + basis._2,
                    basis._3
                  ))
                }
              }
            case _ =>
              competitorPointsMap.updateWith(fight.getFightResult.getWinnerId) { u =>
                val basis = u.getOrElse((0, 0, fight.getGroupId))
                Some((
                  pointsDescriptor.map(_.getWinnerPoints.toInt).getOrElse(0) + basis._1,
                  pointsDescriptor.map(_.getWinnerAdditionalPoints.toInt).getOrElse(0) + basis._2,
                  basis._3
                ))
              }
              fight.winnerId.map { it =>
                competitorPointsMap.updateWith(it) { u =>
                  val basis = u.getOrElse((0, 0, fight.getGroupId))
                  Some((
                    pointsDescriptor.map(_.getLoserPoints.toInt).getOrElse(0) + basis._1,
                    pointsDescriptor.map(_.getLoserAdditionalPoints.toInt).getOrElse(0) + basis._2,
                    basis._3
                  ))
                }
              }
          }
        }
        Right(competitorPointsMap.toList.sortBy { pair => pair._2._1 * 10000 + pair._2._2 }.zipWithIndex.map { v =>
          val (e, i) = v
          new CompetitorStageResultDTO().setRound(0).setGroupId(e._2._3).setCompetitorId(e._1).setPoints(e._2._1)
            .setPlace(i + 1).setStageId(stageId)
        })
      case _ => Left(Errors.InternalError("Stage is not finished."))
    }
  }
}
