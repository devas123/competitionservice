package compman.compsrv.logic

import cats.Semigroupal
import cats.implicits._
import compman.compsrv.model.Errors
import compservice.model.protobuf.model._

import java.util.UUID

package object fight {
  type CanFail[A]  = Either[Errors.Error, A]
  type ThreeFights = (FightDescription, FightDescription, FightDescription)
  type TwoFights   = (FightDescription, FightDescription)

  val SEMI_FINAL        = "Semi-final"
  val QUARTER_FINAL     = "Quarter-final"
  val FINAL             = "Final"
  val WINNER_FINAL      = "Winner-final"
  val GRAND_FINAL       = "Grand final"
  val ELIMINATION       = "Elimination"
  val THIRD_PLACE_FIGHT = "Third place"

  import compman.compsrv.model.extensions._

  val zero: Int = 0

  def generatePlaceholderCompetitorsForGroup(size: Int): List[Competitor] = {
    (0 until size).map { it => Competitor().withId(s"placeholder-$it").withPlaceholder(true) }.toList
  }

  def createEmptyScore: Score = new Score().withAdvantages(0).withPenalties(0).withPoints(0).withPointGroups(Seq.empty)

  def createCompscoreForGroup(
    competitorId: Option[String],
    placeHolderId: Option[String],
    order: Int,
    parentReferenceType: FightReferenceType = FightReferenceType.PROPAGATED
  ): CompScore = {
    CompScore().withCompetitorId(competitorId.orNull).withPlaceholderId(placeHolderId.orNull).withOrder(order)
      .withParentReferenceType(parentReferenceType).withScore(createEmptyScore)
  }

  def createFightId: String = UUID.randomUUID().toString

  def fightDescription(
    competitionId: String,
    categoryId: String,
    stageId: String,
    round: Int,
    roundType: StageRoundType,
    numberInRound: Int,
    durationSeconds: Int,
    fightName: String,
    groupId: String
  ): FightDescription = {
    FightDescription().withId(createFightId).withCategoryId(categoryId).withRound(round)
      .withNumberInRound(numberInRound).withCompetitionId(competitionId).withDuration(durationSeconds)
      .withRoundType(roundType).withStageId(stageId).withFightName(fightName).withStatus(FightStatus.PENDING)
      .withPriority(0).withGroupId(groupId).withFightName(s"Round ${round + 1} fight ${numberInRound + 1}")
      .withScores(Seq.empty)
  }

  def createPairs[T](competitors: List[T], competitors2: Option[List[T]] = None): List[(T, T)] = Semigroupal[List]
    .product(competitors, competitors2.getOrElse(competitors))

  def createScores(ids: List[String], refTypes: List[FightReferenceType]): CanFail[List[CompScore]] = {
    for {
      _ <- assertE(
        ids.size == refTypes.size || refTypes.size == 1,
        Some("The sizes of ids and refTypes should match, or there should be exactly one refType.")
      )
    } yield
      if (ids.size == refTypes.size) { ids.zip(refTypes).zipWithIndex.map(p => compScore(p._1._2, p._1._1, p._2)) }
      else { ids.zipWithIndex.map(p => compScore(refTypes.head, p._1, p._2)) }
  }

  def assertSingleFinal(winnerFightsAndGrandFinal: List[FightDescription]): CanFail[Unit] = {
    assertE(winnerFightsAndGrandFinal.count { it => it.roundType == StageRoundType.GRAND_FINAL } == 1)
  }

  def mergeAll(
    pairs: List[(FightDescription, FightDescription)],
    fightsList: List[FightDescription]
  ): List[ThreeFights] = pairs.zip(fightsList).map { t => (t._1._1, t._1._2, t._2) }

  val priority: StageRoundType => Int = {
    case StageRoundType.GRAND_FINAL       => 0
    case StageRoundType.THIRD_PLACE_FIGHT => 1
    case StageRoundType.WINNER_BRACKETS   => 2
    case StageRoundType.LOSER_BRACKETS    => 3
    case StageRoundType.GROUP             => 4
    case _                                => Int.MaxValue
  }

  def getMaxRound(fights: Iterable[FightDescription]): Int = fights.toList.map(_.round).maxOption.getOrElse(0)

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
  ): CompetitorStageResult = {
    CompetitorStageResult().withStageId(stageId).withCompetitorId(competitorId).withPoints(0).withRound(round)
      .withRoundType(roundType).withPlace(place)
  }

  def createResultForFight(
    stageId: String,
    fight: Option[FightDescription],
    winnerPlace: Int,
    loserPlace: Option[Int] = None
  ): Seq[CompetitorStageResult] = {
    fight.map(f =>
      f.scores.map { score =>
        val place =
          if (f.winnerId.contains(score.getCompetitorId) || f.winnerId.isEmpty) winnerPlace
          else loserPlace.getOrElse(winnerPlace + 1)
        competitorStageResult(stageId, score.getCompetitorId, f.roundOrZero, f.roundType, place)
      }
    ).getOrElse(Seq.empty)
  }

  def generateCurrentRoundFights(
    numberOfFightsInCurrentRound: Int,
    competitionId: String,
    categoryId: String,
    stageId: String,
    currentRound: Int,
    roundType: StageRoundType,
    durationSeconds: Int
  ): List[FightDescription] = (0 until numberOfFightsInCurrentRound).map { index =>
    fightDescription(
      competitionId,
      categoryId,
      stageId,
      currentRound,
      roundType,
      index,
      durationSeconds,
      s"Round ${currentRound + 1}, fight #${index + 1}",
      null
    )
  }.toList

  def compScore(refType: FightReferenceType, p: String, i: Int): CompScore = {
    CompScore().withParentReferenceType(refType).withParentFightId(p).withOrder(i)
  }

  val connectLoseLose: ((FightDescription, FightDescription, FightDescription)) => CanFail[
    (FightDescription, FightDescription, FightDescription)
  ] = (it: (FightDescription, FightDescription, FightDescription)) => {
    for { scores <- createScores(List(it._1.id, it._2.id), List(FightReferenceType.LOSER)) } yield (
      it._1.copy(loseFight = Option(it._3.id)),
      it._2.copy(loseFight = Option(it._3.id)),
      it._3.copy(scores = scores)
    )
  }

  val connectLoseWin: ((FightDescription, FightDescription, FightDescription)) => CanFail[
    (FightDescription, FightDescription, FightDescription)
  ] = (it: (FightDescription, FightDescription, FightDescription)) => {
    for {
      scores <- createScores(List(it._1.id, it._2.id), List(FightReferenceType.LOSER, FightReferenceType.WINNER))
    } yield (
      it._1.copy(loseFight = Option(it._3.id)),
      it._2.copy(winFight = Option(it._3.id)),
      it._3.copy(scores = scores)
    )
  }

  val connectWinWin: ((FightDescription, FightDescription, FightDescription)) => CanFail[
    (FightDescription, FightDescription, FightDescription)
  ] = (it: (FightDescription, FightDescription, FightDescription)) => {
    for { scores <- createScores(List(it._1.id, it._2.id), List(FightReferenceType.WINNER)) } yield (
      it._1.copy(winFight = Option(it._3.id)),
      it._2.copy(winFight = Option(it._3.id)),
      it._3.copy(scores = scores)
    )
  }

}
