package compman.compsrv.logic.service.generate

import cats.Eval
import compman.compsrv.logic.service.FightsService.{FINAL, GRAND_FINAL, SEMI_FINAL, THIRD_PLACE_FIGHT}
import compman.compsrv.model.Errors
import compman.compsrv.model.dto.brackets.{BracketType, CompetitorStageResultDTO, FightReferenceType, StageRoundType, StageStatus, StageType}
import compman.compsrv.model.dto.competition.{CompScoreDTO, CompetitorDTO, FightDescriptionDTO, FightStatus}

import scala.util.Random

object Brackets {
  import compman.compsrv.model.extension._
  import cats.implicits._

  def getLastRoundFightForCompetitor(fights: List[FightDescriptionDTO], cid: String): CanFail[FightDescriptionDTO] = {
    val a = fights.filter(f => f.containsFighter(cid) && !f.winnerId.contains(cid)).maxByOption(_.getRound)
    val b = fights.filter(f => f.getStatus == FightStatus.UNCOMPLETABLE && f.containsFighter(cid))
      .maxByOption(_.getRound)
    a.orElse(b).toRight(Errors.InternalError(Some(s"Cannot find the last round fight for competitor $cid")))
  }

  private def getCompetitorsSetFromFights(fights: List[FightDescriptionDTO]): Set[String] = {
    fights.mapFilter(_.scores).flatten.mapFilter(cs => Option(cs.getCompetitorId)).toSet
  }

  private def connectPreviousAndCurrentRound(
                                              previousRoundFights: List[FightDescriptionDTO],
                                              currentRoundFights: List[FightDescriptionDTO],
                                              connectFun: ThreeFights => CanFail[ThreeFights]
                                            ): CanFail[List[ThreeFights]] = {
    val indexedFights              = previousRoundFights.zipWithIndex
    val previousRoundFightsOdd     = indexedFights.filter { p => p._2 % 2 == 0 }.map(_._1)
    val previousRoundFightsEven    = indexedFights.filter { p => p._2 % 2 == 1 }.map(_._1)
    val previousRoundFightsInPairs = previousRoundFightsOdd.zip(previousRoundFightsEven)
    mergeAll(previousRoundFightsInPairs, currentRoundFights).traverse(connectFun)
  }

  def generateEmptyWinnerRoundsForCategory(
                                            competitionId: String,
                                            categoryId: String,
                                            stageId: String,
                                            compssize: Int,
                                            duration: BigDecimal
                                          ): CanFail[List[FightDescriptionDTO]] = {
    val numberOfRounds = Integer.highestOneBit(nextPowerOfTwo(compssize))

    val iteration = (
                      result: List[FightDescriptionDTO],
                      previousRoundFights: List[FightDescriptionDTO],
                      currentRound: Int,
                      totalRounds: Int
                    ) => {
      if (currentRound < 0 || currentRound >= totalRounds) { Eval.now(Right(Right(result))) }
      else {
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
            Eval.now(Right(Right(crfs.map(it => it.copy(fightName = FINAL, roundType = StageRoundType.GRAND_FINAL)))))
          } else { Eval.now(Left((result, crfs, currentRound + 1, totalRounds))) }
        } else {
          val either = for {
            connectedFights <- connectPreviousAndCurrentRound(previousRoundFights, crfs, connectWinWin)
            res =
              if (currentRound == totalRounds - 1) {
                //    assert(connectedFights.size == 1) { "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }
                Right(result ++ connectedFights.flatMap { it =>
                  List(
                    it._1.copy(fightName = SEMI_FINAL),
                    it._2.copy(fightName = SEMI_FINAL),
                    it._3.copy(fightName = FINAL, roundType = StageRoundType.GRAND_FINAL)
                  )
                })
              } else {
                Left(
                  result ++ connectedFights.flatMap { it => List(it._1, it._2) },
                  connectedFights.map { it => it._3 },
                  currentRound + 1,
                  totalRounds
                )
              }
          } yield res
          either.fold(er => Eval.now(Right(Left(er))), r => Eval.later(r.map(list => Right(list))))
        }
      }
    }

    (List.empty[FightDescriptionDTO], List.empty[FightDescriptionDTO], 0, numberOfRounds)
      .tailRecM[Eval, CanFail[List[FightDescriptionDTO]]](iteration.tupled).value
  }

  def generateStageResultForCompetitor(
                                        stageId: String,
                                        roundType: StageRoundType,
                                        fights: List[FightDescriptionDTO],
                                        maxRound: Int
                                      )(cid: String): CanFail[CompetitorStageResultDTO] = {
    for { lastFight <- getLastRoundFightForCompetitor(fights, cid) } yield competitorStageResult(
      stageId,
      cid,
      lastFight.roundOrZero,
      roundType,
      calculateLoserPlaceForFinalStage(lastFight.roundOrZero, maxRound)
    )
  }
  def distributeCompetitors(
                                     competitors: List[CompetitorDTO],
                                     fights: Map[String, FightDescriptionDTO],
                                     bracketType: BracketType
                                   ): CanFail[Map[String, FightDescriptionDTO]] = {
    bracketType match {
      case BracketType.SINGLE_ELIMINATION | BracketType.DOUBLE_ELIMINATION => for {
        _ <- assertE(
          fights.size * 2 >= competitors.size,
          Some(
            s"Number of fights in the first round is ${fights.size}, which is less than required to fit ${competitors.size} competitors."
          )
        )
        firstRoundFights = fights.values.filter { it =>
          it.roundOrZero == 0 && !it.roundType.contains(StageRoundType.LOSER_BRACKETS)
        }
        coms            = Random.shuffle(competitors)
        pairsWithFights = coms.drop(competitors.size).zip(coms.take(competitors.size)).zip(firstRoundFights)
        updatedFirstRoundFights = pairsWithFights.mapFilter { tr =>
          val ((c1, c2), f) = tr
          for {
            f1 <- f.pushCompetitor(c1.getId)
            f2 <- f1.pushCompetitor(c2.getId)
          } yield f2
        }
        _ <-
          assertE(updatedFirstRoundFights.size == pairsWithFights.size, Some("Not all competitors were distributed."))
      } yield fights ++ updatedFirstRoundFights.groupMapReduce(_.getId)(identity)((a, _) => a)
      case _ => Left(Errors.InternalError(Some("Unsupported brackets type $bracketType")))
    }
  }

  def buildStageResults(
                         bracketType: BracketType,
                         stageStatus: StageStatus,
                         stageType: StageType,
                         fights: List[FightDescriptionDTO],
                         stageId: String
                       ): CanFail[List[CompetitorStageResultDTO]] = {
    stageStatus match {
      case StageStatus.FINISHED => bracketType match {
        case BracketType.SINGLE_ELIMINATION => stageType match {
          case StageType.PRELIMINARY => generatePreliminarySingleElimination(fights, stageId)
          case StageType.FINAL       => generateFinalSingleElimination(fights, stageId)
        }
        case BracketType.DOUBLE_ELIMINATION => stageType match {
          case StageType.PRELIMINARY => Left(Errors.InternalError(Some(
            "Preliminary double elimination is not supported. Returning all the competitors."
          )))
          case StageType.FINAL => generateFinalDoubleElimination(fights, stageId)
        }
        case _ => Left(Errors.InternalError(Some(s"$bracketType is not supported. Returning all the competitors.")))
      }
      case _ => Right(List.empty)
    }
  }

  private def generateLoserBracketAndGrandFinalForWinnerBracket(
                                                                 competitionId: String,
                                                                 categoryId: String,
                                                                 stageId: String,
                                                                 winnerFightsAndGrandFinal: List[FightDescriptionDTO],
                                                                 duration: BigDecimal
                                                               ): CanFail[List[FightDescriptionDTO]] = {
    for {
      _ <- assertSingleFinal(winnerFightsAndGrandFinal)
      _ <- assertE(
        winnerFightsAndGrandFinal.filter { it => !it.roundType.contains(StageRoundType.GRAND_FINAL) }.forall { it =>
          it.roundType.contains(StageRoundType.WINNER_BRACKETS) && it.round != null
        },
        Some("Winner brackets fights contain not winner-brackets round types.")
      )
      _ <- assertE(
        !winnerFightsAndGrandFinal.exists { it =>
          it.scores.exists(_.exists { dto => dto.getParentReferenceType == FightReferenceType.LOSER })
        },
        Some("Winner brackets fights contain contain references from loser brackets.")
      )
      winnerFights = winnerFightsAndGrandFinal.filter { it => !it.roundType.contains(StageRoundType.GRAND_FINAL) } :+
        winnerFightsAndGrandFinal.find { it => it.roundType.contains(StageRoundType.GRAND_FINAL) }
          .map(_.copy(roundType = StageRoundType.WINNER_BRACKETS)).get
      totalWinnerRounds = getMaxRound(winnerFights) + 1
      grandFinal = fightDescription(
        competitionId,
        categoryId,
        stageId,
        totalWinnerRounds,
        StageRoundType.GRAND_FINAL,
        0,
        duration,
        GRAND_FINAL,
        null
      )
      totalLoserRounds       = 2 * (totalWinnerRounds - 1)
      firstWinnerRoundFights = winnerFights.filter { it => it.round.contains(0) }
      loserBracketsSize      = firstWinnerRoundFights.size / 2
      _ <- assertE(
        (loserBracketsSize & (loserBracketsSize - 1)) == 0,
        Some(s"Loser brackets size should be a power of two, but it is $loserBracketsSize")
      )
      createLoserFightNodes = (
                                result: List[FightDescriptionDTO],
                                previousLoserRoundFights: List[FightDescriptionDTO],
                                winnerFights: List[FightDescriptionDTO],
                                currentLoserRound: Int,
                                currentWinnerRound: Int
                              ) =>
        if (totalWinnerRounds <= 0 || totalLoserRounds <= 0) { Eval.now(Right(Right(result))) }
        else {
          val numberOfFightsInCurrentRound =
            if (currentLoserRound % 2 == 0) { loserBracketsSize / 1 << (currentLoserRound / 2) }
            else { previousLoserRoundFights.size }
          val currentLoserRoundFights = currentRoundFights(
            numberOfFightsInCurrentRound,
            competitionId,
            categoryId,
            stageId,
            currentLoserRound,
            StageRoundType.LOSER_BRACKETS,
            duration
          )
          val errorOrEither = for {
            connectedFights <-
              if (currentLoserRound == 0) {
                //this is the first loser brackets round
                //we take the first round of the winner brackets and connect them via loserFights to the generated fights
                connectPreviousAndCurrentRound(firstWinnerRoundFights, currentLoserRoundFights, connectLoseLose)
              } else {
                if (currentLoserRound % 2 == 0) {
                  //it means there will be no competitors falling from the upper bracket.
                  connectPreviousAndCurrentRound(previousLoserRoundFights, currentLoserRoundFights, connectWinWin)
                } else {
                  //we need to merge the winners of fights from the previous loser rounds
                  //and the losers of the fights from the previous winner round
                  val winnerRoundFights = winnerFights.filter { it => it.round.contains(currentWinnerRound) }
                  assert(winnerRoundFights.size == previousLoserRoundFights.size)
                  val allFights = (winnerRoundFights ++ previousLoserRoundFights).sortBy { it =>
                    it.getNumberInRound * 10 + it.roundType.map(priority).getOrElse(Int.MaxValue)
                  }
                  connectPreviousAndCurrentRound(allFights, currentLoserRoundFights, connectLoseWin)
                }
              }
            a =
              if (currentLoserRound == totalLoserRounds - 1) {
                assert(
                  connectedFights.size == 1
                ) /*{ "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }*/
                val lastTuple = connectedFights.head
                Right(
                  for {
                    sc <- createScores(List(lastTuple._1.getId, lastTuple._3.getId), List(FightReferenceType.WINNER))
                    connectedGrandFinal = grandFinal.copy(scores = sc.toArray)
                  } yield result :+ lastTuple._1.copy(winFight = connectedGrandFinal.getId) :+ lastTuple._2 :+
                    lastTuple._3.copy(winFight = connectedGrandFinal.getId) :+ connectedGrandFinal
                )
              } else {
                Left((
                  result ++ connectedFights.flatMap { it => List(it._1, it._2) },
                  connectedFights.map { it => it._3 },
                  winnerFights,
                  currentLoserRound + 1,
                  currentWinnerRound + ((currentLoserRound + 1) % 2)
                ))
              }
          } yield a
          errorOrEither.fold(err => Eval.now(Right(Left(err))), e => Eval.later(e))
        }
      res <- (List.empty[FightDescriptionDTO], List.empty[FightDescriptionDTO], winnerFights, 0, 0)
        .tailRecM[Eval, CanFail[List[FightDescriptionDTO]]](createLoserFightNodes.tupled).value
    } yield res
  }

  def generateDoubleEliminationBracket(
                                        competitionId: String,
                                        categoryId: String,
                                        stageId: String,
                                        compssize: Int,
                                        duration: BigDecimal
                                      ): CanFail[List[FightDescriptionDTO]] = {
    for {
      winnerRounds <- generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stageId, compssize, duration)
      res <-
        generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, stageId, winnerRounds, duration)
    } yield res
  }

  def generateThirdPlaceFightForOlympicSystem(
                                               competitionId: String,
                                               categoryId: String,
                                               stageId: String,
                                               winnerFights: List[FightDescriptionDTO]
                                             ): CanFail[List[FightDescriptionDTO]] =
    if (winnerFights.isEmpty) { Right(winnerFights) }
    else {
      for {
        _ <- assertSingleFinal(winnerFights)
        _ <- assertE(winnerFights.filter { it => it.roundType.contains(StageRoundType.GRAND_FINAL) }.forall { it =>
          it.roundType.contains(StageRoundType.WINNER_BRACKETS) && it.round.isDefined
        })
        semiFinal       = getMaxRound(winnerFights) - 1
        semiFinalFights = winnerFights.filter { it => it.roundOrZero == semiFinal }
        _ <- assertE(
          semiFinalFights.size == 2,
          Some(s"There should be exactly two semifinal fights, but there are ${winnerFights
            .count { it => it.roundOrZero == semiFinal }}")
        )
        thirdPlaceFight = fightDescription(
          competitionId,
          categoryId,
          stageId,
          semiFinal + 1,
          StageRoundType.THIRD_PLACE_FIGHT,
          0,
          semiFinalFights.head.getDuration,
          THIRD_PLACE_FIGHT,
          null
        )
        sc <- createScores(semiFinalFights.map { f => f.getId }, List(FightReferenceType.LOSER))
        updatedFights = List(
          semiFinalFights.head.copy(loseFight = thirdPlaceFight.getId),
          semiFinalFights(1).copy(loseFight = thirdPlaceFight.getId),
          thirdPlaceFight.copy(scores = sc.toArray)
        )
      } yield winnerFights.map { it =>
        if (it.getId == updatedFights.head.getId) updatedFights.head
        else if (it.getId == updatedFights(1).getId) updatedFights(1)
        else it
      } :+ updatedFights(2)
    }

  private def generateFinalDoubleElimination(
                                              fights: List[FightDescriptionDTO],
                                              stageId: String
                                            ): CanFail[List[CompetitorStageResultDTO]] = {
    for {
      finalRoundFight <- fights.find(_.getRoundType == StageRoundType.GRAND_FINAL)
        .toRight(Errors.InternalError(Some("Could not find the grand final.")))
      finalists = List(
        finalRoundFight.winnerId.map { wid =>
          competitorStageResult(stageId, wid, finalRoundFight.roundOrZero, finalRoundFight.getRoundType, 1)
        },
        finalRoundFight.loserId.map { lid =>
          competitorStageResult(stageId, lid, finalRoundFight.roundOrZero, finalRoundFight.getRoundType, 2)
        }
      ).mapFilter(identity)
      competitorIds   = getCompetitorsSetFromFights(fights) -- finalists.mapFilter(cs => Option(cs.getCompetitorId))
      loserFights     = fights.filter(it => it.roundType.contains(StageRoundType.LOSER_BRACKETS))
      finalLoserRound = getMaxRound(loserFights)
      others <- competitorIds.toList.traverse { cid =>
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
        val place = placesChunks.filter(pc => pc._1 < it._1).foldLeft(0)((acc, p) => acc + p._2.size)
        it._2.map { c => c.setPlace(place + 3) }
      }
    } yield finalists ++ otherPlaces
  }

  private def generateFinalSingleElimination(
                                              fights: List[FightDescriptionDTO],
                                              stageId: String
                                            ): CanFail[List[CompetitorStageResultDTO]] = {
    import cats.implicits._

    for {
      grandFinal <- fights.find(it => it.getRoundType == StageRoundType.GRAND_FINAL)
        .toRight(Errors.InternalError(Some("The stage is a final stage but has no grand final.")))
      thirdPlaceFight         = fights.find(it => it.getRoundType == StageRoundType.THIRD_PLACE_FIGHT)
      grandFinalAndThirdPlace = getCompetitorsSetFromFights(List(Some(grandFinal), thirdPlaceFight).mapFilter(identity))
      competitors             = getCompetitorsSetFromFights(fights) -- grandFinalAndThirdPlace
      thirdPlaceResults       = createResultForFight(stageId, thirdPlaceFight, 3)
      grandFinalResults       = createResultForFight(stageId, Some(grandFinal), 1)
      otherFightsResults = competitors.toList.mapFilter(
        generateStageResultForCompetitor(stageId, grandFinal.getRoundType, fights, grandFinal.roundOrZero)(_).toOption
      )
      res = grandFinalResults ++ thirdPlaceResults ++ otherFightsResults

    } yield res.toList
  }

  def calculateLoserPlaceForFinalStage(round: Int, finalRound: Int): Int = {
    val diff = finalRound - round
    diff * 2 + 1
  }

  private def generatePreliminarySingleElimination(
                                                    fights: List[FightDescriptionDTO],
                                                    stageId: String
                                                  ): CanFail[List[CompetitorStageResultDTO]] = Right {
    import cats.implicits._
    val lastRound        = getMaxRound(fights)
    val lastRoundFights  = fights.filter { it => it.getRound == lastRound }
    val lastRoundWinners = lastRoundFights.mapFilter(f => f.winnerId).toSet
    val lastRoundLosers  = lastRoundFights.mapFilter(f => f.loserId).toSet
    val competitorIds    = getCompetitorsSetFromFights(fights)

    def calculateLoserPlace(round: Int): Int = {
      val diff = lastRound - round
      diff * lastRoundFights.size + 1
    }
    val winnerRoundType = lastRoundFights.headOption.flatMap(_.roundType).getOrElse(StageRoundType.WINNER_BRACKETS)

    def compStageResult(cid: String, place: Int) =
      competitorStageResult(stageId, cid, lastRound, winnerRoundType, place)

    lastRoundWinners.map(compStageResult(_, 1)) ++ lastRoundLosers.map(compStageResult(_, 2)) ++
      competitorIds.toList.mapFilter { it =>
        if (lastRoundWinners.contains(it) || lastRoundLosers.contains(it)) None
        else (for { lastLostRoundFight <- getLastRoundFightForCompetitor(fights, it) } yield competitorStageResult(
          stageId,
          it,
          lastLostRoundFight.roundOrZero,
          lastLostRoundFight.getRoundType,
          calculateLoserPlace(lastLostRoundFight.getRound)
        )).toOption
      }
  }.map(_.toList)

}
