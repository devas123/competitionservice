package compman.compsrv.logic

import cats.Semigroupal
import cats.implicits._
import compman.compsrv.model.Errors
import compman.compsrv.model.dto.brackets.{CompetitorStageResultDTO, FightReferenceType, StageRoundType}
import compman.compsrv.model.dto.competition._

import java.util.UUID

package object fight {
  type CanFail[A] = Either[Errors.Error, A]
  type ThreeFights = (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)
  type TwoFights = (FightDescriptionDTO, FightDescriptionDTO)

  val SEMI_FINAL        = "Semi-final"
  val QUARTER_FINAL     = "Quarter-final"
  val FINAL             = "Final"
  val WINNER_FINAL      = "Winner-final"
  val GRAND_FINAL       = "Grand final"
  val ELIMINATION       = "Elimination"
  val THIRD_PLACE_FIGHT = "Third place"


  import compman.compsrv.model.extensions._

  val zero: java.math.BigDecimal = BigDecimal(0).bigDecimal

  def generatePlaceholderCompetitorsForGroup(size: Int): List[CompetitorDTO] = {
    (0 until size).map { it => new CompetitorDTO().setId(s"placeholder-$it").setPlaceholder(true) }.toList
  }

  def createEmptyScore: ScoreDTO = new ScoreDTO()
    .setAdvantages(0)
    .setPenalties(0)
    .setPoints(0)
    .setPointGroups(Array.empty)

  def createCompscoreForGroup(competitorId: Option[String], placeHolderId: Option[String], order: Int, parentReferenceType: FightReferenceType = FightReferenceType.PROPAGATED): CompScoreDTO = {
    new CompScoreDTO()
      .setCompetitorId(competitorId.orNull)
      .setPlaceholderId(placeHolderId.orNull)
      .setOrder(order)
      .setParentReferenceType(parentReferenceType)
      .setScore(createEmptyScore)
  }


  def createFightId: String = UUID.randomUUID().toString

  def fightDescription(
                        competitionId: String,
                        categoryId: String,
                        stageId: String,
                        round: Int,
                        roundType: StageRoundType,
                        numberInRound: Int,
                        duration: BigDecimal,
                        fightName: String,
                        groupId: String
                      ): FightDescriptionDTO = {
    new FightDescriptionDTO()
      .setId(createFightId)
      .setCategoryId(categoryId)
      .setRound(round)
      .setNumberInRound(numberInRound)
      .setCompetitionId(competitionId)
      .setDuration(duration.bigDecimal)
      .setRoundType(roundType)
      .setStageId(stageId)
      .setFightName(fightName)
      .setStatus(FightStatus.PENDING)
      .setPriority(0)
      .setGroupId(groupId)
      .setFightName(s"Round ${round + 1} fight ${numberInRound + 1}")
      .setScores(Array.empty)
  }


  def createPairs[T](competitors: List[T], competitors2: Option[List[T]] = None): List[(T, T)] =
    Semigroupal[List].product(competitors, competitors2.getOrElse(competitors))

  def createScores(ids: List[String], refTypes: List[FightReferenceType]): CanFail[List[CompScoreDTO]] = {
    for {
      _ <- assertE(ids.size == refTypes.size || refTypes.size == 1,
        Some("The sizes of ids and refTypes should match, or there should be exactly one refType.")
      )
    } yield if (ids.size == refTypes.size) {
      ids.zip(refTypes).zipWithIndex.map(p => compScore(p._1._2, p._1._1, p._2))
    }
    else {
      ids.zipWithIndex.map(p => compScore(refTypes.head, p._1, p._2))
    }
  }

  def assertSingleFinal(winnerFightsAndGrandFinal: List[FightDescriptionDTO]): CanFail[Unit] = {
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
      StageRoundType.GRAND_FINAL       -> 0
      StageRoundType.THIRD_PLACE_FIGHT -> 1
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
      .setPoints(0)
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

  def generateCurrentRoundFights(
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
