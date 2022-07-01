package compman.compsrv.logic.fight

import cats.{Monad, Traverse}
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.Utils.groupById
import compman.compsrv.logic.fight.CompetitorSelectionUtils._
import compservice.model.protobuf.eventpayload.CompetitorAssignmentDescriptor
import compservice.model.protobuf.model._

object FightUtils {

  val finishedStatuses: Seq[FightStatus] = List(FightStatus.UNCOMPLETABLE, FightStatus.FINISHED, FightStatus.WALKOVER)
  val unMovableFightStatuses: Seq[FightStatus] = finishedStatuses :+ FightStatus.IN_PROGRESS
  val notFinishedStatuses: Seq[FightStatus] =
    List(FightStatus.PENDING, FightStatus.IN_PROGRESS, FightStatus.GET_READY, FightStatus.PAUSED)
  def ceilingNextPowerOfTwo(x: Int): Int = 1 << (32 - Integer.numberOfLeadingZeros(x - 1))

  def applyStageInputDescriptorToResultsAndFights[F[+_]: Monad: Interpreter](
    stageToSendCompetitorsTo: StageDescriptor,
    stageGraph: DiGraph,
    competitorStageResults: Map[String, Seq[CompetitorStageResult]],
    fights: Map[String, FightDescription]
  ): F[List[String]] = {
    for {
      descriptor <- OptionT.fromOption[F](stageToSendCompetitorsTo.inputDescriptor)
      program =
        if (descriptor.selectors.nonEmpty) {
          stageToSendCompetitorsTo.inputDescriptor.map(_.selectors).get.flatMap(it => {
            val classifier = it.classifier
            classifier match {
              case SelectorClassifier.LAST_N_PLACES => List(lastNPlaces(it.applyToStageId, it.selectorValue.head.toInt))
              case SelectorClassifier.MANUAL        => List(returnIds(it.selectorValue.toList))
              case _ => List(firstNPlaces(it.applyToStageId, it.selectorValue.head.toInt))
            }
          }).reduce((a, b) => CompetitorSelectionUtils.and(a, b))
        } else {
          firstNPlaces(
            stageGraph.incomingConnections(stageToSendCompetitorsTo.id).ids.head,
            descriptor.numberOfCompetitors
          )
        }
      res <- OptionT.liftF(program.foldMap(Interpreter[F].interepret(competitorStageResults, fights)).map(_.toList))
    } yield res
  }.value.map(_.getOrElse(List.empty))

  def filterPreliminaryFights(
    outputSize: Int,
    fights: List[FightDescription],
    bracketType: BracketType
  ): List[FightDescription] = {
    val result = bracketType match {
      case BracketType.SINGLE_ELIMINATION =>
        val ceiling = ceilingNextPowerOfTwo(outputSize)
        val roundsToReturn = fights.groupMapReduce(_.round)(_ => 1)((a, b) => a + b).filter { case (_, v) =>
          v * 2 > ceiling
        }.keySet
        fights.filter(it => roundsToReturn.contains(it.round))
      case _ => fights
    }
    result.map(f => f.withConnections(f.connections.filter(con => result.exists(r => r.id == con.fightId))))
  }

  def updateIfConditionIsMet[F[_]: Monad](
    coll: List[FightDescription]
  )(condition: FightDescription => Boolean, update: FightDescription => FightDescription): F[List[FightDescription]] =
    Traverse[List].traverse(coll)(f => if (condition(f)) Monad[F].pure(update(f)) else Monad[F].pure(f))

  def advanceFighterToSiblingFights[F[_]: Monad](
    competitorId: String,
    sourceFight: String,
    referenceType: FightReferenceType,
    fights: Map[String, FightDescription]
  ): F[(Map[String, FightDescription], List[CompetitorAssignmentDescriptor])] = {
    Monad[F].tailRecM((competitorId, sourceFight, referenceType, fights, List.empty[CompetitorAssignmentDescriptor])) {
      case (cid, sf, rt, fs, up) =>
        val eitherT = for {
          fight <- fs.get(sf)
          normalizedRt = if (rt == FightReferenceType.PROPAGATED) FightReferenceType.WINNER else rt
          targetFightId <- fight.connections.find(_.referenceType == normalizedRt)
          targetFight   <- fs.get(targetFightId.fightId)
          update = targetFight.withScores(targetFight.scores.map(s =>
            if (s.parentFightId.contains(sf) && s.parentReferenceType.contains(rt)) { s.withCompetitorId(cid) }
            else s
          ))
          updatedFights = fs + (targetFight.id -> update)
          assignment = CompetitorAssignmentDescriptor().withToFightId(targetFightId.fightId).withFromFightId(sf)
            .withCompetitorId(cid).withReferenceType(rt)
          res =
            if (targetFight.status == FightStatus.UNCOMPLETABLE) {
              Left((cid, targetFight.id, FightReferenceType.PROPAGATED, updatedFights, up :+ assignment))
            } else { Right((updatedFights, up :+ assignment)) }
        } yield res
        Monad[F].pure(eitherT.getOrElse(Right((fs, up))))
    }
  }

  def markAndProcessUncompletableFights[F[_]: Monad](
    fights: Map[String, FightDescription]
  ): F[Map[String, FightDescription]] = {
    for {
      marked    <- markUncompletableFights[F](fights)
      processed <- advanceCompetitorsInUncompletableFights[F](marked)
    } yield marked ++ processed
  }

  def markUncompletableFights[F[_]: Monad](fights: Map[String, FightDescription]): F[Map[String, FightDescription]] = {
    def markAsUncompletable(it: FightDescription) = {
      Option(it.scores).flatMap(_.find(_.competitorId.isDefined)).map(cs =>
        it.withStatus(FightStatus.UNCOMPLETABLE)
          .withFightResult(FightResult(cs.competitorId, Some(FightResultOptionConstants.WALKOVER.id), Some("BYE")))
      ).getOrElse(it.withStatus(FightStatus.UNCOMPLETABLE))
    }

    for {
      uncompletableFights <- fights.values.filter(_.id.nonEmpty).toList.traverse(it =>
        for { canBePacked <- checkIfFightIsPackedOrCanBePackedEventually[F](it.id, fights) } yield
          if (canBePacked) it else markAsUncompletable(it)
      )
    } yield fights ++ groupById(uncompletableFights)(_.id)
  }

  def advanceCompetitorsInUncompletableFights[F[_]: Monad](
    markedFights: Map[String, FightDescription]
  ): F[Map[String, FightDescription]] = {
    def getUncompletableFightScores(uncompletableFights: Map[String, FightDescription]) = {
      uncompletableFights.values.flatMap(f => f.scores.map(s => (s.competitorId, f.id))).filter(_._1.isDefined).toList
    }
    for {
      uncompletableFights <- Monad[F].pure(markedFights.filter(e => e._2.status == FightStatus.UNCOMPLETABLE))
      uncompletableFightsScores = getUncompletableFightScores(uncompletableFights)
      mapped <- uncompletableFightsScores
        .foldM((markedFights, List.empty[CompetitorAssignmentDescriptor]))((acc, elem) =>
          for {
            p <- advanceFighterToSiblingFights[F](elem._1.get, elem._2, FightReferenceType.WINNER, acc._1)
            assignments = acc._2 ++ p._2
          } yield (p._1, assignments)
        )
    } yield mapped._1
  }

  private def checkIfFightIsPackedOrCanBePackedEventually[F[_]: Monad](
    fightId: String,
    fights: Map[String, FightDescription]
  ): F[Boolean] = {
    def getFightScores(fightId: String) = fights.get(fightId).flatMap(f => Option(f.scores))

    def checkIfFightCanProduceReference(fightId: String, referenceType: FightReferenceType): Boolean = {
      val fightScores = getFightScores(fightId)
      val parentFights = fightScores.map(_.map(it => it.parentReferenceType -> it.parentFightId))
        .map(_.filter(it => it._1.isDefined && it._2.isDefined)).map(_.map(it => it._1.get -> it._2.get))

      val fightScoresWithCompetitors = fightScores.map(_.filter(_.competitorId.isDefined))
      if (!fightScoresWithCompetitors.exists(_.nonEmpty)) {
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

    def scoreCanProduceReference(sc: CompScore) = {
      if (sc.competitorId.isDefined) { true }
      else if (sc.parentFightId.isDefined && sc.parentReferenceType.isDefined) {
        checkIfFightCanProduceReference(sc.getParentFightId, sc.getParentReferenceType)
      } else { false }
    }

    (for {
      scores <- OptionT.fromOption[F](getFightScores(fightId))
      canProduce = scores.size >= 2 && scores.forall(scoreCanProduceReference)
    } yield canProduce).value.map(_.getOrElse(false))
  }

}
