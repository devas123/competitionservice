package compman.compsrv.logic.fight

import cats.Monad
import cats.data.EitherT
import compman.compsrv.Utils.groupById
import compman.compsrv.logic._
import compman.compsrv.model.Errors
import compman.compsrv.Utils
import compservice.model.protobuf.model._
import scala.util.Random

object BracketsUtils {
  import cats.implicits._
  import compman.compsrv.model.extensions._

  def getLastRoundFightForCompetitor(fights: List[FightDescription], cid: String): CanFail[FightDescription] = {
    val a = fights.filter(f => f.containsFighter(cid) && !f.winnerId.contains(cid)).maxByOption(_.round)
    val b = fights.filter(f => f.status == FightStatus.UNCOMPLETABLE && f.containsFighter(cid))
      .maxByOption(_.round)
    a.orElse(b).toRight(Errors.InternalError(Some(s"Cannot find the last round fight for competitor $cid")))
  }

  private def getCompetitorsSetFromFights(fights: List[FightDescription]): Set[String] = {
    fights.flatMap(_.scores).mapFilter(cs => cs.competitorId).toSet
  }

  private def connectPreviousAndCurrentRound(
    previousRoundFights: List[FightDescription],
    currentRoundFights: List[FightDescription],
    connectFun: ThreeFights => CanFail[ThreeFights]
  ): CanFail[List[ThreeFights]] = {
    val indexedFights              = previousRoundFights.zipWithIndex
    val previousRoundFightsOdd     = indexedFights.filter { p => p._2 % 2 == 0 }.map(_._1)
    val previousRoundFightsEven    = indexedFights.filter { p => p._2 % 2 == 1 }.map(_._1)
    val previousRoundFightsInPairs = previousRoundFightsOdd.zip(previousRoundFightsEven)
    mergeAll(previousRoundFightsInPairs, currentRoundFights).traverse(connectFun)
  }

  def generateEmptyWinnerRoundsForCategory[F[+_]: Monad](
    competitionId: String,
    categoryId: String,
    stageId: String,
    compssize: Int,
    durationSeconds: Int
  ): F[CanFail[List[FightDescription]]] = {
    val numberOfRounds = Integer.numberOfTrailingZeros(Integer.highestOneBit(nextPowerOfTwo(compssize)))

    val iteration = (
      result: List[FightDescription],
      previousRoundFights: List[FightDescription],
      currentRound: Int,
      totalRounds: Int
    ) => {
      if (currentRound < 0 || currentRound >= totalRounds) { Monad[F].pure(Right(Right(result))) }
      else {
        val numberOfFightsInCurrentRound = 1 << (totalRounds - currentRound - 1)
        val crfs = generateCurrentRoundFights(
          numberOfFightsInCurrentRound,
          competitionId,
          categoryId,
          stageId,
          currentRound,
          StageRoundType.WINNER_BRACKETS,
          durationSeconds
        )
        if (currentRound == 0) {
          //this is the first round
          if (currentRound == totalRounds - 1) {
            //this is the final round, it means there's only one fight.
            Monad[F]
              .pure(Right(Right(crfs.map(it => it.copy(fightName = Some(FINAL), roundType = StageRoundType.GRAND_FINAL)))))
          } else { Monad[F].pure(Left((result, crfs, currentRound + 1, totalRounds))) }
        } else {
          val either = for {
            connectedFights <- connectPreviousAndCurrentRound(previousRoundFights, crfs, connectWinWin)
            res =
              if (currentRound == totalRounds - 1) {
                //    assert(connectedFights.size == 1) { "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }
                Right(result ++ connectedFights.flatMap { it =>
                  List(
                    it._1.copy(fightName = Some(SEMI_FINAL)),
                    it._2.copy(fightName = Some(SEMI_FINAL)),
                    it._3.copy(fightName = Some(FINAL), roundType = StageRoundType.GRAND_FINAL)
                  )
                })
              } else {
                Left((
                  result ++ connectedFights.flatMap { it => List(it._1, it._2) },
                  connectedFights.map { it => it._3 },
                  currentRound + 1,
                  totalRounds
                ))
              }
          } yield res
          either.fold(er => Monad[F].pure(Right(Left(er))), r => Monad[F].pure(r.map(list => Right(list))))
        }
      }
    }

    (List.empty[FightDescription], List.empty[FightDescription], 0, numberOfRounds)
      .tailRecM[F, CanFail[List[FightDescription]]](iteration.tupled)
  }

  def generateStageResultForCompetitor(
    stageId: String,
    fights: List[FightDescription],
    maxRound: Int
  )(cid: String): CanFail[CompetitorStageResult] = {
    for { lastFight <- getLastRoundFightForCompetitor(fights, cid) } yield competitorStageResult(
      stageId,
      cid,
      lastFight.roundOrZero,
      lastFight.roundType,
      calculateLoserPlaceForFinalStage(lastFight.roundOrZero, maxRound)
    )
  }
  def distributeCompetitors(
    competitors: List[Competitor],
    fights: Map[String, FightDescription]
  ): CanFail[Map[String, FightDescription]] = {
    def checkAllFightsWereUpdated(secondLoopPairsWithFightsSize: Int, secondLoopUpdatedFightsSize: Int) = {
      assertE(
        secondLoopPairsWithFightsSize == secondLoopUpdatedFightsSize,
        Some(
          s"Not all competitors were distributed. Updated fights: $secondLoopUpdatedFightsSize, expected updates: $secondLoopPairsWithFightsSize"
        )
      )
    }

    for {
      firstRoundFights <- Right {
        fights.values.filter { it => it.roundOrZero == 0 && it.roundType != StageRoundType.LOSER_BRACKETS }
      }
      _ <- assertE(
        firstRoundFights.size * 2 >= competitors.size,
        Some(
          s"Number of fights in the first round is ${firstRoundFights.size}, which is less than required to fit ${competitors.size} competitors."
        )
      )
      firstRoundFightsEnriched = firstRoundFights.map(f =>
        if (f.scores.isEmpty) f.withScores(Array(0, 1).map(i =>
          CompScore()
            .clearCompetitorId
            .clearPlaceholderId
            .withOrder(i)
            .withScore(createEmptyScore)
            .clearParentFightId
            .withParentReferenceType(FightReferenceType.PROPAGATED)
        ))
        else f
      )

      coms                    = Random.shuffle(competitors)
      pairsWithFights         = coms.take(firstRoundFightsEnriched.size).zip(firstRoundFightsEnriched)
      updatedFirstRoundFights = pairsWithFights.mapFilter { case (c1, f) => f.pushCompetitor(c1.id) }
      _ <- checkAllFightsWereUpdated(pairsWithFights.size, updatedFirstRoundFights.size)
      secondLoopPairsWithFights = updatedFirstRoundFights.take(coms.size - updatedFirstRoundFights.size)
        .zip(coms.drop(updatedFirstRoundFights.size))
      secondLoopUpdatedFights = secondLoopPairsWithFights.mapFilter { case (f, c1) => f.pushCompetitor(c1.id) }
      _ <- checkAllFightsWereUpdated(secondLoopPairsWithFights.size, secondLoopUpdatedFights.size)
      result = secondLoopUpdatedFights ++ updatedFirstRoundFights.drop(secondLoopUpdatedFights.size)
    } yield fights ++ groupById(result)(_.id)
  }

  def buildStageResults(
    bracketType: BracketType,
    stageStatus: StageStatus,
    stageType: StageType,
    fights: List[FightDescription],
    stageId: String
  ): CanFail[List[CompetitorStageResult]] = {
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

  def getNumberOfFightsInCurrentRound(
    loserBracketsSize: Int,
    previousLoserRoundFights: List[FightDescription],
    currentLoserRound: Int
  ): Int = {
    if (currentLoserRound % 2 == 0) { loserBracketsSize / 1 << (currentLoserRound / 2) }
    else { previousLoserRoundFights.size }
  }

  def connectWinnerAndLoserFights(
    previousLoserRoundFights: List[FightDescription],
    currentLoserRoundFights: List[FightDescription],
    winnerFights: List[FightDescription],
    currentWinnerRound: Int,
    currentLoserRound: Int
  ): CanFail[List[ThreeFights]] = {
    if (currentLoserRound % 2 == 0) {
      //it means there will be no competitors falling from the upper bracket.
      connectPreviousAndCurrentRound(previousLoserRoundFights, currentLoserRoundFights, connectWinWin)
    } else {
      //we need to merge the winners of fights from the previous loser rounds
      //and the losers of the fights from the previous winner round
      val winnerRoundFights = winnerFights.filter { it => it.round == currentWinnerRound }
      for {
        _ <- assertE(winnerRoundFights.size == previousLoserRoundFights.size)
        allFights = (winnerRoundFights ++ previousLoserRoundFights).sortBy { it =>
          it.numberInRound * 10 + priority(it.roundType)
        }
        res <- connectPreviousAndCurrentRound(allFights, currentLoserRoundFights, connectLoseWin)
      } yield res
    }
  }

  def connectLastLoserRound(
    grandFinal: FightDescription,
    result: List[FightDescription],
    lastTuple: ThreeFights
  ): CanFail[List[FightDescription]] = {
    for {
      sc <- createScores(List(lastTuple._1.id, lastTuple._3.id), List(FightReferenceType.WINNER))
      connectedGrandFinal = grandFinal.copy(scores = sc.toArray)
    } yield result :+ lastTuple._1.copy(winFight = Option(connectedGrandFinal.id)) :+ lastTuple._2 :+
      lastTuple._3.copy(winFight = Option(connectedGrandFinal.id)) :+ connectedGrandFinal
  }

  private def generateLoserBracketAndGrandFinalForWinnerBracket[F[+_]: Monad](
    competitionId: String,
    categoryId: String,
    stageId: String,
    winnerFightsAndGrandFinal: List[FightDescription],
    durationSeconds: Int
  ): F[CanFail[List[FightDescription]]] = {

    val eitherT = for {
      _ <- EitherT.fromEither[F](assertSingleFinal(winnerFightsAndGrandFinal))
      _ <- assertET[F](
        winnerFightsAndGrandFinal.filter { it => it.roundType != StageRoundType.GRAND_FINAL }.forall { it =>
          it.roundType == StageRoundType.WINNER_BRACKETS
        },
        Some("Winner brackets fights contain not winner-brackets round types.")
      )
      _ <- assertET[F](
        !winnerFightsAndGrandFinal.exists { it =>
          it.scores.exists { dto => dto.parentReferenceType.contains(FightReferenceType.LOSER) }
        },
        Some("Winner brackets fights contain contain references from loser brackets.")
      )
      winnerFights = winnerFightsAndGrandFinal.filter { it => it.roundType != StageRoundType.GRAND_FINAL } :+
        winnerFightsAndGrandFinal.find { it => it.roundType == StageRoundType.GRAND_FINAL }
          .map(_.copy(roundType = StageRoundType.WINNER_BRACKETS)).get
      totalWinnerRounds = getMaxRound(winnerFights) + 1
      grandFinal = fightDescription(
        competitionId,
        categoryId,
        stageId,
        totalWinnerRounds,
        StageRoundType.GRAND_FINAL,
        0,
        durationSeconds,
        GRAND_FINAL,
        null
      )
      totalLoserRounds       = 2 * (totalWinnerRounds - 1)
      firstWinnerRoundFights = winnerFights.filter { it => it.round == 0 }
      loserBracketsSize      = firstWinnerRoundFights.size / 2
      _ <- assertET[F](
        (loserBracketsSize & (loserBracketsSize - 1)) == 0,
        Some(s"Loser brackets size should be a power of two, but it is $loserBracketsSize")
      )
      createLoserFightNodes = (
        result: List[FightDescription],
        previousLoserRoundFights: List[FightDescription],
        winnerFights: List[FightDescription],
        currentLoserRound: Int,
        currentWinnerRound: Int
      ) =>
        if (totalWinnerRounds <= 0 || totalLoserRounds <= 0) { Monad[F].pure(Right(Right(result))) }
        else {
          val numberOfFightsInCurrentRound =
            getNumberOfFightsInCurrentRound(loserBracketsSize, previousLoserRoundFights, currentLoserRound)
          val currentLoserRoundFights = generateCurrentRoundFights(
            numberOfFightsInCurrentRound,
            competitionId,
            categoryId,
            stageId,
            currentLoserRound,
            StageRoundType.LOSER_BRACKETS,
            durationSeconds
          )
          val errorOrEither = for {
            connectedFights <-
              if (currentLoserRound == 0) {
                //this is the first loser brackets round
                //we take the first round of the winner brackets and connect them via loserFights to the generated fights
                connectPreviousAndCurrentRound(firstWinnerRoundFights, currentLoserRoundFights, connectLoseLose)
              } else {
                connectWinnerAndLoserFights(
                  previousLoserRoundFights,
                  currentLoserRoundFights,
                  winnerFights,
                  currentWinnerRound,
                  currentLoserRound
                )
              }
            a =
              if (currentLoserRound == totalLoserRounds - 1) {
                assert(
                  connectedFights.size == 1
                ) /*{ "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }*/
                val lastTuple = connectedFights.head
                Right(connectLastLoserRound(grandFinal, result, lastTuple))
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
          errorOrEither.fold(err => Monad[F].pure(Right(Left(err))), e => Monad[F].pure(e))
        }
      res <- EitherT(
        (List.empty[FightDescription], List.empty[FightDescription], winnerFights, 0, 0)
          .tailRecM[F, CanFail[List[FightDescription]]](createLoserFightNodes.tupled)
      )
    } yield res
    eitherT.value
  }

  def generateDoubleEliminationBracket[F[+_]: Monad](
    competitionId: String,
    categoryId: String,
    stageId: String,
    compssize: Int,
    durationSeconds: Int
  ): F[CanFail[List[FightDescription]]] = {
    (for {
      winnerRounds <-
        EitherT(generateEmptyWinnerRoundsForCategory[F](competitionId, categoryId, stageId, compssize, durationSeconds))
      res <- EitherT(generateLoserBracketAndGrandFinalForWinnerBracket[F](
        competitionId,
        categoryId,
        stageId,
        winnerRounds,
        durationSeconds
      ))
    } yield res).value
  }

  def generateThirdPlaceFightForOlympicSystem(
    competitionId: String,
    categoryId: String,
    stageId: String,
    winnerFights: List[FightDescription]
  ): CanFail[List[FightDescription]] =
    if (winnerFights.isEmpty) { Right(winnerFights) }
    else {
      for {
        _ <- assertSingleFinal(winnerFights)
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
          semiFinalFights.head.duration,
          THIRD_PLACE_FIGHT,
          null
        )
        sc <- createScores(semiFinalFights.map { f => f.id }, List(FightReferenceType.LOSER))
        updatedFights = List(
          semiFinalFights.head.copy(loseFight = Option(thirdPlaceFight.id)),
          semiFinalFights(1).copy(loseFight = Option(thirdPlaceFight.id)),
          thirdPlaceFight.copy(scores = sc.toArray)
        )
      } yield winnerFights.map { it =>
        if (it.id == updatedFights.head.id) updatedFights.head
        else if (it.id == updatedFights(1).id) updatedFights(1)
        else it
      } :+ updatedFights(2)
    }

  private def generateFinalDoubleElimination(
    fights: List[FightDescription],
    stageId: String
  ): CanFail[List[CompetitorStageResult]] = {
    for {
      finalRoundFight <- fights.find(_.roundType == StageRoundType.GRAND_FINAL)
        .toRight(Errors.InternalError(Some("Could not find the grand final.")))
      finalists = List(
        finalRoundFight.winnerId.map { wid =>
          competitorStageResult(stageId, wid, finalRoundFight.roundOrZero, finalRoundFight.roundType, 1)
        },
        finalRoundFight.loserId.map { lid =>
          competitorStageResult(stageId, lid, finalRoundFight.roundOrZero, finalRoundFight.roundType, 2)
        }
      ).mapFilter(identity)
      competitorIds   = getCompetitorsSetFromFights(fights) -- finalists.map(_.competitorId)
      loserFights     = fights.filter(it => it.roundType == StageRoundType.LOSER_BRACKETS)
      finalLoserRound = getMaxRound(loserFights)
      others <- competitorIds.toList.traverse { cid =>
        for {
          lastLostRoundFight <- getLastRoundFightForCompetitor(fights, cid)
          place = finalLoserRound - lastLostRoundFight.roundOrZero
        } yield competitorStageResult(
          stageId,
          cid,
          lastLostRoundFight.roundOrZero,
          lastLostRoundFight.roundType,
          place
        )
      }
      placesChunks = others.groupBy(_.place).toList.sortBy(_._1)
      otherPlaces = placesChunks.flatMap { it =>
        val place = placesChunks.filter(pc => pc._1 < it._1).foldLeft(0)((acc, p) => acc + p._2.size)
        it._2.map { c => c.withPlace(place + 3) }
      }
    } yield finalists ++ otherPlaces
  }

  private def generateFinalSingleElimination(
    inputFights: List[FightDescription],
    stageId: String
  ): CanFail[List[CompetitorStageResult]] = {
    for {
      fights <- FightUtils.markAndProcessUncompletableFights[CanFail](Utils.groupById(inputFights)(_.id))
      grandFinal <- fights.find(it => it._2.roundType == StageRoundType.GRAND_FINAL)
        .toRight(Errors.InternalError(Some("The stage is a final stage but has no grand final."))).map(_._2)
      thirdPlaceFight         = fights.find(it => it._2.roundType == StageRoundType.THIRD_PLACE_FIGHT).map(_._2)
      grandFinalAndThirdPlace = getCompetitorsSetFromFights(List(Some(grandFinal), thirdPlaceFight).mapFilter(identity))
      competitors             = getCompetitorsSetFromFights(fights.values.toList) -- grandFinalAndThirdPlace
      thirdPlaceResults       = createResultForFight(stageId, thirdPlaceFight, 3)
      grandFinalResults       = createResultForFight(stageId, Some(grandFinal), 1)
      otherFightsResults = competitors.toList.mapFilter(
        generateStageResultForCompetitor(
          stageId,
          fights.values.toList,
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

  private def generatePreliminarySingleElimination(
    fights: List[FightDescription],
    stageId: String
  ): CanFail[List[CompetitorStageResult]] = Right {
    import cats.implicits._
    val lastRound        = getMaxRound(fights)
    val lastRoundFights  = fights.filter { it => it.round == lastRound }
    val lastRoundWinners = lastRoundFights.mapFilter(f => f.winnerId).toSet
    val lastRoundLosers  = lastRoundFights.mapFilter(f => f.loserId).toSet
    val competitorIds    = getCompetitorsSetFromFights(fights)

    def calculateLoserPlace(round: Int): Int = {
      val diff = lastRound - round
      diff * lastRoundFights.size + 1
    }
    val winnerRoundType = lastRoundFights.headOption.map(_.roundType).getOrElse(StageRoundType.WINNER_BRACKETS)

    def compStageResult(cid: String, place: Int) =
      competitorStageResult(stageId, cid, lastRound, winnerRoundType, place)

    lastRoundWinners.map(compStageResult(_, 1)) ++ lastRoundLosers.map(compStageResult(_, 2)) ++
      competitorIds.toList.mapFilter { it =>
        if (lastRoundWinners.contains(it) || lastRoundLosers.contains(it)) None
        else (for { lastLostRoundFight <- getLastRoundFightForCompetitor(fights, it) } yield competitorStageResult(
          stageId,
          it,
          lastLostRoundFight.roundOrZero,
          lastLostRoundFight.roundType,
          calculateLoserPlace(lastLostRoundFight.round)
        )).toOption
      }
  }.map(_.toList)

}
