package compman.compsrv.logic.service

import cats.implicits._
import compman.compsrv.logic.service.generate.FightsGenerateService.createScores
import compman.compsrv.logic.service.FightsService.fightDescription
import compman.compsrv.model.dto.brackets.{CompetitorStageResultDTO, FightReferenceType, StageRoundType}
import compman.compsrv.model.dto.competition.{CompScoreDTO, FightDescriptionDTO}
import compman.compsrv.model.Errors

package object generate {
  type CanFail[A]  = Either[Errors.Error, A]
  type ThreeFights = (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)
  type TwoFights   = (FightDescriptionDTO, FightDescriptionDTO)

  import compman.compsrv.model.extension._
  val zero: java.math.BigDecimal = BigDecimal(0).bigDecimal

  def assertE(condition: => Boolean, message: => Option[String] = None): Either[Errors.InternalError, Unit] =
    if (condition)
      Right(())
    else
      Left(Errors.InternalError(message))

  def assertSingleFinal(winnerFightsAndGrandFinal: List[FightDescriptionDTO]): Either[Errors.InternalError, Unit] = {
    assertE(
      winnerFightsAndGrandFinal.count { it =>
        it.roundType.contains(StageRoundType.GRAND_FINAL) && it.round != null
      } == 1
    )
  }

  def mergeAll(
    pairs: List[(FightDescriptionDTO, FightDescriptionDTO)],
    fightsList: List[FightDescriptionDTO]
  ): List[ThreeFights] = pairs
    .zip(fightsList)
    .map { t =>
      (t._1._1, t._1._2, t._2)
    }

  val priority: Map[StageRoundType, Int] = {
    import StageRoundType._
    Map {
      GRAND_FINAL       -> 0
      THIRD_PLACE_FIGHT -> 1
      WINNER_BRACKETS   -> 2
      LOSER_BRACKETS    -> 3
      GROUP             -> 4
    }.withDefaultValue(Int.MaxValue)
  }

  def getMaxRound(fights: Iterable[FightDescriptionDTO]): Int = fights.toList.mapFilter(_.round).maxOption.getOrElse(0)

  def nextPowerOfTwo(y: Int): Int = {
    var x = y
    x = x - 1
    x = x | (x >> 1)
    x = x | (x >> 2)
    x = x | (x >> 4)
    x = x | (x >> 8)
    x = x | (x >> 16)
    x + 1
  }

  def competitorStageResult(
    stageId: String,
    competitorId: String,
    round: Int,
    roundType: StageRoundType,
    place: Int
  ): CompetitorStageResultDTO = {
    new CompetitorStageResultDTO()
      .setStageId(stageId)
      .setCompetitorId(competitorId)
      .setPoints(zero)
      .setRound(round)
      .setRoundType(roundType)
      .setPlace(place)
  }

  def createResultForFight(
    stageId: String,
    thirdPlaceFight: Option[FightDescriptionDTO],
    winnerPlace: Int,
    loserPlace: Option[Int] = None
  ): Array[CompetitorStageResultDTO] = {
    thirdPlaceFight
      .flatMap(f =>
        f.scores
          .map(
            _.map { score =>
              val place =
                if (f.winnerId.contains(f.getCompetitionId))
                  winnerPlace
                else
                  loserPlace.getOrElse(winnerPlace + 1)
              competitorStageResult(stageId, score.getCompetitorId, f.roundOrZero, f.getRoundType, place)
            }
          )
      )
      .getOrElse(Array.empty)
  }

  def currentRoundFights(
    numberOfFightsInCurrentRound: Int,
    competitionId: String,
    categoryId: String,
    stageId: String,
    currentRound: Int,
    roundType: StageRoundType,
    duration: BigDecimal
  ): List[FightDescriptionDTO] = (0 until numberOfFightsInCurrentRound)
    .map { index =>
      fightDescription(
        competitionId,
        categoryId,
        stageId,
        currentRound,
        roundType,
        index,
        duration,
        s"Round ${currentRound + 1}, fight #${index + 1}",
        null
      )
    }
    .toList

  def compScore(refType: FightReferenceType, p: String, i: Int): CompScoreDTO = {
    new CompScoreDTO().setParentReferenceType(refType).setParentFightId(p).setOrder(i)
  }

  val connectLoseLose: ((FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)) => CanFail[
    (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)
  ] =
    (it: (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)) => {
      for {
        scores <- createScores(List(it._1.getId, it._2.getId), List(FightReferenceType.LOSER))
      } yield (
        it._1.copy(loseFight = it._3.getId),
        it._2.copy(loseFight = it._3.getId),
        it._3.copy(scores = scores.toArray)
      )
    }

  val connectLoseWin: ((FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)) => CanFail[
    (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)
  ] =
    (it: (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)) => {
      for {
        scores <- createScores(
          List(it._1.getId, it._2.getId),
          List(FightReferenceType.LOSER, FightReferenceType.WINNER)
        )
      } yield (
        it._1.copy(loseFight = it._3.getId),
        it._2.copy(winFight = it._3.getId),
        it._3.copy(scores = scores.toArray)
      )
    }

  val connectWinWin: ((FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)) => CanFail[
    (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)
  ] =
    (it: (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)) => {
      for {
        scores <- createScores(List(it._1.getId, it._2.getId), List(FightReferenceType.WINNER))
      } yield (
        it._1.copy(winFight = it._3.getId),
        it._2.copy(winFight = it._3.getId),
        it._3.copy(scores = scores.toArray)
      )
    }

}
