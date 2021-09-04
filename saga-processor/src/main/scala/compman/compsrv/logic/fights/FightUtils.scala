package compman.compsrv.logic.fights

import cats.{Monad, Traverse}
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.logic.fights.CompetitorSelectionUtils._
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CompScoreDTO, FightDescriptionDTO, FightResultDTO, FightStatus}
import compman.compsrv.model.CompetitionState
import compman.compsrv.model.events.payload.CompetitorAssignmentDescriptor

object FightUtils {

  val finishedStatuses = List(FightStatus.UNCOMPLETABLE, FightStatus.FINISHED, FightStatus.WALKOVER)
  val unMovableFightStatuses: Seq[FightStatus] = finishedStatuses :+ FightStatus.IN_PROGRESS
  val notFinishedStatuses =
    List(FightStatus.PENDING, FightStatus.IN_PROGRESS, FightStatus.GET_READY, FightStatus.PAUSED)
  def ceilingNextPowerOfTwo(x: Int): Int = 1 << (32 - Integer.numberOfLeadingZeros(x - 1))

  def applyStageInputDescriptorToResultsAndFights[F[+_]: Monad: Interpreter](
    descriptor: StageInputDescriptorDTO,
    previousStageId: String,
    state: CompetitionState
  ): F[List[String]] = {
    val program =
      if (Option(descriptor.getSelectors).exists(_.nonEmpty)) {
        descriptor.getSelectors.flatMap(it => {
          val classifier = it.getClassifier
          classifier match {
            case SelectorClassifier.FIRST_N_PLACES =>
              List(firstNPlaces(it.getApplyToStageId, it.getSelectorValue.head.toInt))
            case SelectorClassifier.LAST_N_PLACES =>
              List(lastNPlaces(it.getApplyToStageId, it.getSelectorValue.head.toInt))
            case SelectorClassifier.MANUAL => List(returnIds(it.getSelectorValue.toList))
          }
        }).reduce((a, b) => CompetitorSelectionUtils.and(a, b))
      } else { firstNPlaces(previousStageId, descriptor.getNumberOfCompetitors) }
    program.foldMap(Interpreter[F].interepret(state)).map(_.toList)
  }

  def filterPreliminaryFights[F[_]: Monad](
    outputSize: Int,
    fights: List[FightDescriptionDTO],
    bracketType: BracketType
  ): F[List[FightDescriptionDTO]] = {
    val result = bracketType match {
      case BracketType.SINGLE_ELIMINATION =>
        val ceiling = ceilingNextPowerOfTwo(outputSize)
        val roundsToReturn = fights.groupMapReduce(_.getRound)(_ => 1)((a, b) => a + b).filter { case (_, v) =>
          v * 2 > ceiling
        }.keySet
        fights.filter(it => roundsToReturn.contains(it.getRound))
      case _ => fights
    }
    for {
      a <- update[F](result)(
        it => it.getWinFight != null && result.exists(r => { r.getId == it.getWinFight }),
        _.setWinFight(null)
      )
      b <- update[F](a)(
        it => it.getWinFight != null && result.exists(r => { r.getId == it.getLoseFight }),
        _.setLoseFight(null)
      )
    } yield b
  }

  def update[F[_]: Monad](coll: List[FightDescriptionDTO])(
    condition: FightDescriptionDTO => Boolean,
    update: FightDescriptionDTO => FightDescriptionDTO
  ): F[List[FightDescriptionDTO]] = Traverse[List]
    .traverse(coll)(f => if (condition(f)) Monad[F].pure(update(f)) else Monad[F].pure(f))

  def advanceFighterToSiblingFights[F[_]: Monad](
    competitorId: String,
    sourceFight: String,
    referenceType: FightReferenceType,
    fights: Map[String, FightDescriptionDTO]
  ): F[(Map[String, FightDescriptionDTO], List[CompetitorAssignmentDescriptor])] = {
    Monad[F].tailRecM((competitorId, sourceFight, referenceType, fights, List.empty[CompetitorAssignmentDescriptor])) {
      case (cid, sf, rt, fs, up) =>
        val eitherT = for {
          fight <- fs.get(sf)
          targetFightId = if (referenceType == FightReferenceType.LOSER) fight.getLoseFight else fight.getWinFight
          targetFight <- fs.get(targetFightId)
          update = targetFight.setScores(targetFight.getScores.map(s =>
            if (s.getParentFightId == sf && s.getParentReferenceType == rt) { s.setCompetitorId(cid) }
            else s
          ))
          updatedFights = fs + (targetFight.getId -> update)
          assignment = new CompetitorAssignmentDescriptor().setToFightId(targetFightId).setFromFightId(sf)
            .setCompetitorId(cid).setReferenceType(rt)
          res =
            if (targetFight.getStatus == FightStatus.UNCOMPLETABLE) {
              Left((cid, targetFight.getId, FightReferenceType.WINNER, updatedFights, up :+ assignment))
            } else { Right((updatedFights, up :+ assignment)) }
        } yield res
        Monad[F].pure(eitherT.getOrElse(Right((fs, up))))
    }
  }

  def markAndProcessUncompletableFights[F[+_]: Monad](
    fights: Map[String, FightDescriptionDTO]
  ): F[Map[String, FightDescriptionDTO]] = {
    for {
      marked    <- markUncompletableFights[F](fights)
      processed <- advanceCompetitorsInUncompletableFights[F](marked)
    } yield processed
  }

  def markUncompletableFights[F[_]: Monad](
    fights: Map[String, FightDescriptionDTO]
  ): F[Map[String, FightDescriptionDTO]] = {
    def update(it: FightDescriptionDTO) = {
      it.getScores.find(_.getCompetitorId != null).map(cs =>
        it.setStatus(FightStatus.UNCOMPLETABLE)
          .setFightResult(new FightResultDTO(cs.getCompetitorId, FightResultOptionDTO.WALKOVER.getId, "BYE"))
      ).getOrElse(it.setStatus(FightStatus.UNCOMPLETABLE))
    }

    for {
      uncompletableFights <- fights.values.filter(_.getId != null).toList.traverse(it =>
        for { canBePacked <- checkIfFightIsPackedOrCanBePackedEventually[F](it.getId, fights) } yield
          if (canBePacked) it else update(it)
      )
    } yield fights ++ uncompletableFights.groupMapReduce(_.getId)(identity)((a, _) => a)
  }

  def advanceCompetitorsInUncompletableFights[F[_]: Monad](
    markedFights: Map[String, FightDescriptionDTO]
  ): F[Map[String, FightDescriptionDTO]] = {
    def getUncompletableFightScores(uncompletableFights: Map[String, FightDescriptionDTO]) = {
      uncompletableFights.values.flatMap(f => f.getScores.map(s => (s.getCompetitorId, f.getId))).filter(_._1 != null)
        .toList
    }
    for {
      uncompletableFights <- Monad[F].pure(markedFights.filter(e => e._2.getStatus == FightStatus.UNCOMPLETABLE))
      uncompletableFightsScores = getUncompletableFightScores(uncompletableFights)
      mapped <- uncompletableFightsScores
        .foldM((uncompletableFights, List.empty[CompetitorAssignmentDescriptor]))((acc, elem) =>
          advanceFighterToSiblingFights[F](elem._1, elem._2, FightReferenceType.WINNER, acc._1).map(p => {
            val assignments = acc._2 ++ p._2
            (p._1, assignments)
          })
        )
    } yield mapped._1
  }

  private def checkIfFightIsPackedOrCanBePackedEventually[F[_]: Monad](
    fightId: String,
    fights: Map[String, FightDescriptionDTO]
  ): F[Boolean] = {
    def getFightScores(fightId: String) = fights.get(fightId).map(_.getScores)

    def checkIfFightCanProduceReference(fightId: String, referenceType: FightReferenceType): Boolean = {
      val fightScores = getFightScores(fightId)
      val parentFights = fightScores.map(_.map(it => { it.getParentReferenceType -> it.getParentFightId }))
        .map(_.filter(it => it._1 != null && it._2 != null))
      val fightScoresWithCompetitors = fightScores.map(_.filter(_.getCompetitorId != null))
      if (!fightScoresWithCompetitors.exists(_.length > 0)) {
        referenceType match {
          case FightReferenceType.WINNER => parentFights
              .exists(arr => arr.exists(it => { checkIfFightCanProduceReference(it._2, it._1) }))
          case _ => parentFights.exists(it => it.count(tt => { checkIfFightCanProduceReference(tt._2, tt._1) }) >= 2)
        }
      } else if (fightScoresWithCompetitors.exists(_.length == 1)) {
        referenceType match {
          case FightReferenceType.WINNER => true
          case _ => parentFights.exists(_.exists(it => { checkIfFightCanProduceReference(it._2, it._1) }))
        }
      } else { true }
    }

    def scoreCanProduceReference(sc: CompScoreDTO) = {
      if (sc.getCompetitorId != null) { true }
      else if (sc.getParentFightId != null && sc.getParentReferenceType != null) {
        if (sc.getCompetitorId != null) { true }
        else if (sc.getParentFightId != null) {
          checkIfFightCanProduceReference(sc.getParentFightId, sc.getParentReferenceType)
        } else { false }
      } else { false }
    }

    (for {
      scores <- OptionT.fromOption[F](getFightScores(fightId))
      canProduce = scores.size >= 2 && scores.forall(scoreCanProduceReference)
    } yield canProduce).value.map(_.getOrElse(false))
  }

}
