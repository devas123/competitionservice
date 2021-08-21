package compman.compsrv.logic.service.generate

import cats.implicits._
import cats.Traverse
import compman.compsrv.logic.service.FightsService.{fightDescription, FINAL, SEMI_FINAL}
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CompetitorDTO, CompScoreDTO, FightDescriptionDTO, FightStatus}
import compman.compsrv.model.Errors

import scala.util.Random

trait FightsGenerateService {
  def generateStageFights(
      competitionId: String,
      categoryId: String,
      stage: StageDescriptorDTO,
      compssize: Int,
      duration: BigDecimal,
      competitors: List[CompetitorDTO],
      outputSize: Int
  ): PartialFunction[BracketType, List[FightDescriptionDTO]]
}

object FightsGenerateService {
  import compman.compsrv.model.extension._
  private val zero = BigDecimal(0).bigDecimal
  val singleEliminationGenerator: FightsGenerateService =
    (
        competitionId: String,
        categoryId: String,
        stage: StageDescriptorDTO,
        compssize: Int,
        duration: BigDecimal,
        competitors: List[CompetitorDTO],
        outputSize: Int
    ) => { case BracketType.SINGLE_ELIMINATION =>
      List.empty
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

  def mergeAll(
      pairs: List[(FightDescriptionDTO, FightDescriptionDTO)],
      fightsList: List[FightDescriptionDTO]
  ): List[ThreeFights] = pairs
    .zip(fightsList)
    .map { t =>
      (t._1._1, t._1._2, t._2)
    }

  def createScores(
      ids: List[String],
      refTypes: List[FightReferenceType]
  ): CanFail[List[CompScoreDTO]] = {
    if (!(ids.size == refTypes.size || refTypes.size == 1))
      Left {
        Errors.InternalError(
          Some(
            "The sizes of ids and refTypes should match, or there should be exactly one refType."
          )
        )
      }
    else
      Right {
        if (ids.size == refTypes.size) {
          ids.zip(refTypes).zipWithIndex.map(p => compScore(p._1._2, p._1._1, p._2))
        } else {
          ids.zipWithIndex.map(p => compScore(refTypes.head, p._1, p._2))
        }
      }
  }

  private def compScore(refType: FightReferenceType, p: String, i: Int) = {
    new CompScoreDTO().setParentReferenceType(refType).setParentFightId(p).setOrder(i)
  }

  private def getLastRoundFightForCompetitor(
      fights: List[FightDescriptionDTO],
      cid: String
  ): CanFail[FightDescriptionDTO] = {
    val a = fights
      .filter(f => f.containsFighter(cid) && !f.winnerId.contains(cid))
      .maxByOption(_.getRound)
    val b = fights
      .filter(f => f.getStatus == FightStatus.UNCOMPLETABLE && f.containsFighter(cid))
      .maxByOption(_.getRound)
    a.orElse(b)
      .toRight(Errors.InternalError(Some(s"Cannot find the last round fight for competitor $cid")))
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

  private def getMaxRound(fights: Iterable[FightDescriptionDTO]) = fights
    .toList
    .mapFilter(_.round)
    .maxOption
    .getOrElse(0)

  private def getCompetitorsSetFromFights(fights: List[FightDescriptionDTO]): Set[String] = {
    fights.mapFilter(_.scores).flatten.mapFilter(cs => Option(cs.getCompetitorId)).toSet
  }

  type ThreeFights = (FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO)
  type TwoFights   = (FightDescriptionDTO, FightDescriptionDTO)
  type CanFail[A]  = Either[Errors.Error, A]

  def createConnectedTripletsFrom(
      previousRoundFights: List[FightDescriptionDTO],
      currentRoundFights: List[FightDescriptionDTO],
      connectFun: ThreeFights => CanFail[ThreeFights]
  ): CanFail[List[ThreeFights]] = {
    val indexedFights = previousRoundFights.zipWithIndex
    val previousRoundFightsOdd = indexedFights
      .filter { p =>
        p._2 % 2 == 0
      }
      .map(_._1)
    val previousRoundFightsEven = indexedFights
      .filter { p =>
        p._2 % 2 == 1
      }
      .map(_._1)
    val previousRoundFightsInPairs = previousRoundFightsOdd.zip(previousRoundFightsEven)
    mergeAll(previousRoundFightsInPairs, currentRoundFights).traverse(connectFun)
  }

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

  private def currentRoundFights(
      numberOfFightsInCurrentRound: Int,
      competitionId: String,
      categoryId: String,
      stageId: String,
      currentRound: Int,
      roundType: StageRoundType,
      duration: BigDecimal
  ) = (0 until numberOfFightsInCurrentRound)
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

  def generateEmptyWinnerRoundsForCategory(
      competitionId: String,
      categoryId: String,
      stageId: String,
      compssize: Int,
      duration: BigDecimal
  ): CanFail[List[FightDescriptionDTO]] = {
    val numberOfRounds = Integer.highestOneBit(nextPowerOfTwo(compssize))

    def createWinnerFightNodes(
        result: List[FightDescriptionDTO],
        previousRoundFights: List[FightDescriptionDTO],
        currentRound: Int,
        totalRounds: Int
    ): CanFail[List[FightDescriptionDTO]] = {
      if (currentRound >= totalRounds) {
        return Right(result)
      }
      if (currentRound < 0) {
        return Right(result)
      }
      val numberOfFightsInCurrentRound = 1 << (totalRounds - currentRound - 1)
      val crfs = currentRoundFights(
        numberOfFightsInCurrentRound,
        competitionId,
        categoryId,
        stageId,
        currentRound,
        StageRoundType.WINNER_BRACKETS,
        duration
      )
      if (currentRound == 0) {
        //this is the first round
        if (currentRound == totalRounds - 1) {
          //this is the final round, it means there's only one fight.
          Right(
            crfs.map { it =>
              it.copy(fightName = FINAL, roundType = StageRoundType.GRAND_FINAL)
            }
          )
        } else {
          createWinnerFightNodes(result, crfs, currentRound + 1, totalRounds)
        }
      } else {
        for {
          connectedFights <- createConnectedTripletsFrom(previousRoundFights, crfs, connectWinWin)
          res <-
            if (currentRound == totalRounds - 1) {
              //    assert(connectedFights.size == 1) { "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }
              Right(
                result ++
                  connectedFights.flatMap { it =>
                    List(
                      it._1.copy(fightName = SEMI_FINAL),
                      it._2.copy(fightName = SEMI_FINAL),
                      it._3.copy(fightName = FINAL, roundType = StageRoundType.GRAND_FINAL)
                    )
                  }
              )
            } else {
              createWinnerFightNodes(
                result ++
                  connectedFights.flatMap { it =>
                    List(it._1, it._2)
                  },
                connectedFights.map { it =>
                  it._3
                },
                currentRound + 1,
                totalRounds
              )
            }
        } yield res
      }
    }
    createWinnerFightNodes(List.empty, List.empty, 0, numberOfRounds)
  }

  def generateStageResultForCompetitor(
      stageId: String,
      roundType: StageRoundType,
      fights: List[FightDescriptionDTO],
      maxRound: Int
  )(cid: String): CanFail[CompetitorStageResultDTO] = {
    for {
      lastFight <- getLastRoundFightForCompetitor(fights, cid)
    } yield competitorStageResult(
      stageId,
      cid,
      lastFight.roundOrZero,
      roundType,
      calculateLoserPlaceForFinalStage(lastFight.roundOrZero, maxRound)
    )
  }

  private def createResultForFight(
      stageId: String,
      thirdPlaceFight: Option[FightDescriptionDTO],
      winnerPlace: Int,
      loserPlace: Option[Int] = None
  ) = {
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
               competitorStageResult(
                 stageId,
                 score.getCompetitorId,
                 f.roundOrZero,
                 f.getRoundType,
                 place
               )
            }
          )
      )
      .getOrElse(Array.empty)
  }

  private def distributeCompetitors(
      competitors: List[CompetitorDTO],
      fights: Map[String, FightDescriptionDTO],
      bracketType: BracketType
  ): CanFail[Map[String, FightDescriptionDTO]] = {
    bracketType match {
      case BracketType.SINGLE_ELIMINATION | BracketType.DOUBLE_ELIMINATION =>
        for {
          _ <-
            if (fights.size * 2 >= competitors.size)
              Left(
                Errors.InternalError(
                  Some(
                    s"Number of fights in the first round is ${fights
                      .size}, which is less than required to fit ${competitors.size} competitors."
                  )
                )
              )
            else
              Right(())
          firstRoundFights = fights
            .values
            .filter { it =>
              it.roundOrZero == 0 && !it.roundType.contains(StageRoundType.LOSER_BRACKETS)
            }
          coms = Random.shuffle(competitors)
          pairsWithFights = coms
            .drop(competitors.size)
            .zip(coms.take(competitors.size))
            .zip(firstRoundFights)
          updatedFirstRoundFights = pairsWithFights.mapFilter { tr =>
             val ((c1, c2), f) = tr
             for {
               f1 <- f.pushCompetitor(c1.getId)
               f2 <- f1.pushCompetitor(c2.getId)
             } yield f2
          }
          _ <-
            if (updatedFirstRoundFights.size != pairsWithFights.size)
              Left(Errors.InternalError(Some("Not all competitors were distributed.")))
            else
              Right(())
        } yield fights ++ updatedFirstRoundFights.groupMapReduce(_.getId)(identity)((a, _) => a)
      case _ =>
        Left(Errors.InternalError(Some("Unsupported brackets type $bracketType")))
    }
  }

  private def buildStageResults(
      bracketType: BracketType,
      stageStatus: StageStatus,
      stageType: StageType,
      fights: List[FightDescriptionDTO],
      stageId: String
  ): CanFail[List[CompetitorStageResultDTO]] = {
    stageStatus match {
      case StageStatus.FINISHED =>
        bracketType match {
          case BracketType.SINGLE_ELIMINATION =>
            stageType match {
              case StageType.PRELIMINARY =>
                generatePreliminarySingleElimination(fights, stageId)
              case StageType.FINAL =>
                generateFinalSingleElimination(fights, stageId)
            }
          case BracketType.DOUBLE_ELIMINATION =>
            stageType match {
              case StageType.PRELIMINARY =>
                Left(
                  Errors.InternalError(
                    Some(
                      "Preliminary double elimination is not supported. Returning all the competitors."
                    )
                  )
                )
              case StageType.FINAL =>
                generateFinalDoubleElimination(fights, stageId)
            }
          case _ =>
            Left(
              Errors.InternalError(
                Some(s"$bracketType is not supported. Returning all the competitors.")
              )
            )
        }
      case _ =>
        Right(List.empty)
    }
  }

  private def generateFinalDoubleElimination(
      fights: List[FightDescriptionDTO],
      stageId: String
  ): CanFail[List[CompetitorStageResultDTO]] = {
    for {
      finalRoundFight <- fights
        .find(_.getRoundType == StageRoundType.GRAND_FINAL)
        .toRight(Errors.InternalError(Some("Could not find the grand final.")))
      finalists = List(
        finalRoundFight
          .winnerId
          .map { wid =>
            competitorStageResult(
              stageId,
              wid,
              finalRoundFight.roundOrZero,
              finalRoundFight.getRoundType,
              1
            )
          },
        finalRoundFight
          .loserId
          .map { lid =>
            competitorStageResult(
              stageId,
              lid,
              finalRoundFight.roundOrZero,
              finalRoundFight.getRoundType,
              2
            )
          }
      ).mapFilter(identity)
      competitorIds =
        getCompetitorsSetFromFights(fights) -- finalists.mapFilter(cs => Option(cs.getCompetitorId))
      loserFights     = fights.filter(it => it.roundType.contains(StageRoundType.LOSER_BRACKETS))
      finalLoserRound = getMaxRound(loserFights)
      others <-
        Traverse[List].traverse(competitorIds.toList) { cid =>
          for {
            lastLostRoundFight <- getLastRoundFightForCompetitor(fights, cid)
            place = finalLoserRound - lastLostRoundFight.roundOrZero
          } yield competitorStageResult(
            stageId,
            cid,
            lastLostRoundFight.roundOrZero,
            lastLostRoundFight.getRoundType,
            place
          )
        }
      placesChunks = others.groupBy(_.getPlace).toList.sortBy(_._1)
      otherPlaces = placesChunks.flatMap { it =>
         val place =
           placesChunks.filter(pc => pc._1 < it._1).foldLeft(0)((acc, p) => acc + p._2.size)
         it._2
           .map { c =>
             c.setPlace(place + 3)
           }
      }
    } yield finalists ++ otherPlaces
  }

  private def generateFinalSingleElimination(
      fights: List[FightDescriptionDTO],
      stageId: String
  ): CanFail[List[CompetitorStageResultDTO]] = {
    import cats.implicits._

    for {
      grandFinal <- fights
        .find(it => it.getRoundType == StageRoundType.GRAND_FINAL)
        .toRight(Errors.InternalError(Some("The stage is a final stage but has no grand final.")))
      thirdPlaceFight = fights.find(it => it.getRoundType == StageRoundType.THIRD_PLACE_FIGHT)
      grandFinalAndThirdPlace = getCompetitorsSetFromFights(
        List(Some(grandFinal), thirdPlaceFight).mapFilter(identity)
      )
      competitors       = getCompetitorsSetFromFights(fights) -- grandFinalAndThirdPlace
      thirdPlaceResults = createResultForFight(stageId, thirdPlaceFight, 3)
      grandFinalResults = createResultForFight(stageId, Some(grandFinal), 1)
      otherFightsResults = competitors
        .toList
        .mapFilter(
          generateStageResultForCompetitor(
            stageId,
            grandFinal.getRoundType,
            fights,
            grandFinal.roundOrZero
          )(_).toOption
        )
      res = grandFinalResults ++ thirdPlaceResults ++ otherFightsResults

    } yield res.toList
  }

  def calculateLoserPlaceForFinalStage(round: Int, finalRound: Int): Int = {
    val diff = finalRound - round
    diff * 2 + 1
  }

  private def competitorStageResult(
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

  private def generatePreliminarySingleElimination(
      fights: List[FightDescriptionDTO],
      stageId: String
  ): CanFail[List[CompetitorStageResultDTO]] = Right {
    import cats.implicits._
    val lastRound = getMaxRound(fights)
    val lastRoundFights = fights.filter { it =>
      it.getRound == lastRound
    }
    val lastRoundWinners = lastRoundFights.mapFilter(f => f.winnerId).toSet
    val lastRoundLosers  = lastRoundFights.mapFilter(f => f.loserId).toSet
    val competitorIds    = getCompetitorsSetFromFights(fights)

    def calculateLoserPlace(round: Int): Int = {
      val diff = lastRound - round
      diff * lastRoundFights.size + 1
    }
    val winnerRoundType = lastRoundFights
      .headOption
      .flatMap(_.roundType)
      .getOrElse(StageRoundType.WINNER_BRACKETS)

    def compStageResult(cid: String, place: Int) = competitorStageResult(
      stageId,
      cid,
      lastRound,
      winnerRoundType,
      place
    )

    lastRoundWinners.map(compStageResult(_, 1)) ++ lastRoundLosers.map(compStageResult(_, 2)) ++
      competitorIds
        .toList
        .mapFilter { it =>
          if (lastRoundWinners.contains(it) || lastRoundLosers.contains(it))
            None
          else
            (
              for {
                lastLostRoundFight <- getLastRoundFightForCompetitor(fights, it)
              } yield competitorStageResult(
                stageId,
                it,
                lastLostRoundFight.roundOrZero,
                lastLostRoundFight.getRoundType,
                calculateLoserPlace(lastLostRoundFight.getRound)
              )
            ).toOption
        }
  }.map(_.toList)
}
