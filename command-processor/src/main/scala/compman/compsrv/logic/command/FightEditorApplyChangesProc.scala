package compman.compsrv.logic.command

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.implicits._
import compman.compsrv.logic
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight._
import compman.compsrv.model.command.Commands.{FightEditorApplyChangesCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.commandpayload.{CompetitorMovedToGroup, CompetitorsOfFightUpdated, FightEditorApplyChangesPayload, GroupChangeType}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.FightEditorChangesAppliedPayload
import compservice.model.protobuf.model._

import java.util.UUID
import scala.annotation.tailrec

object FightEditorApplyChangesProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ FightEditorApplyChangesCommand(_, _, _) => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: FightEditorApplyChangesCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      _       <- logic.assertETErr[F](state.stages.contains(payload.stageId), Errors.StageDoesNotExist(payload.stageId))
      eventPayload <- EitherT
        .liftF[F, Errors.Error, Option[FightEditorChangesAppliedPayload]](createPayload[F](payload, state))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = eventPayload.map(MessageInfo.Payload.FightEditorChangesAppliedPayload)
      ))
    } yield Seq(event)
    eventT.value
  }

  val changePriority: GroupChangeType => Int = {
    case GroupChangeType.REMOVE => 0
    case GroupChangeType.ADD    => 1
    case _                      => Int.MaxValue
  }

  private def createPayload[F[+_]: Monad](
    payload: FightEditorApplyChangesPayload,
    state: CommandProcessorCompetitionState
  ) = {
    (for {
      rawUpdates <- OptionT.fromOption[F](createUpdates(payload, state))
      allFights = state.fights
      bracketChanges <- OptionT.fromOption[F](Option(payload.bracketsChanges))
      cleanedUpdates = rawUpdates.map { case (id, fight) =>
        id -> (if (FightStatusUtils.isUncompletable(fight)) fight.withStatus(FightStatus.PENDING) else fight)
      }
      cleanedAffected = clearAffectedFights(cleanedUpdates, bracketChanges.map(_.fightId).toSet)
      markedFights <- OptionT.liftF(FightUtils.markAndProcessUncompletableFights[F](cleanedAffected))
      updates   = markedFights.filter(f => allFights.contains(f._1))
      additions = markedFights.filter(f => !allFights.contains(f._1))
      removals = allFights.filter { case (id, fight) => fight.stageId == payload.stageId && !markedFights.contains(id) }
    } yield FightEditorChangesAppliedPayload().withNewFights(additions.values.toSeq).withUpdates(updates.values.toSeq)
      .withRemovedFighids(removals.keys.toSeq)).value
  }

  @tailrec
  private def clearAffectedFights(
    fights: Map[String, FightDescription],
    changedIds: Set[String]
  ): Map[String, FightDescription] = {
    if (changedIds.isEmpty) { fights }
    else {
      val affectedFights = fights.filter { case (_, fight) =>
        fight.scores.exists(_.parentFightId.exists(changedIds.contains))
      }
      clearAffectedFights(
        fights.map { case (id, fight) =>
          id -> fight.withScores(
            fight.scores.map(it => if (it.parentFightId.exists(changedIds.contains)) it.clearCompetitorId else it)
          )
        },
        affectedFights.keySet
      )
    }
  }

  private def createUpdates(payload: FightEditorApplyChangesPayload, state: CommandProcessorCompetitionState) = {
    for {
      stage <- state.stages.get(payload.stageId)
      allStageFights = state.fights.filter(_._2.stageId == payload.stageId)
      bracketsType   = stage.bracketType
    } yield applyChanges(payload, allStageFights, stage, bracketsType)
  }

  private def applyChanges(
    payload: FightEditorApplyChangesPayload,
    allStageFights: Map[String, FightDescription],
    stage: StageDescriptor,
    bracketsType: BracketType
  ) = {
    bracketsType match {
      case BracketType.GROUP =>
        val groups                     = stage.groupDescriptors
        val fightsByGroupId            = allStageFights.values.groupBy(_.getGroupId)
        val competitorChangesByGroupId = payload.competitorMovedToGroups.groupBy(_.groupId)
        groups.flatMap(gr => {
          val groupChanges = competitorChangesByGroupId.getOrElse(gr.id, Seq.empty)
          val groupFights  = fightsByGroupId.getOrElse(gr.id, Iterable.empty).map(f => (f.id, f)).toMap
          groupChanges.sortBy(c => changePriority(c.changeType)).foldLeft(groupFights)((acc, ch) => {
            ch.changeType match {
              case GroupChangeType.REMOVE => removeCompetitorsFromGroup(acc, ch, gr)
              case GroupChangeType.ADD    => addCompetitorToGroup(groupFights, ch)
              case _                      => acc
            }
          })
        }).sortBy(it => { it._2.numberInRound }).zipWithIndex.map { case ((_, fight), index) =>
          fight.withNumberInRound(index)
        }.map(f => (f.id, f)).toMap
      case _ => updateEliminationBrackets(allStageFights, payload.bracketsChanges)
    }
  }

  private def getMinUnusedOrder(scores: Seq[CompScore], index: Int): Int = {
    if (scores.isEmpty) { 0 }
    else { (0 to scores.length + index).filter(i => { !scores.exists(s => s.order == i) })(index) }
  }

  private def updateEliminationBrackets(
    allStageFights: Map[String, FightDescription],
    bracketChanges: Seq[CompetitorsOfFightUpdated]
  ) = {
    allStageFights.map { case (id, fight) =>
      id -> bracketChanges.find(change => change.fightId == id).map(change => applyChangeToFight(fight, change))
        .getOrElse(fight)
    }
  }

  private def applyChangeToFight(f: FightDescription, change: CompetitorsOfFightUpdated) = {
    if (change.competitors.isEmpty) { f.clearScores }
    else {
      val scores = f.scores
      f.withScores(change.competitors.zipWithIndex.map { case (cmpId, index) =>
        scores.find(_.competitorId.contains(cmpId))
          .orElse(scores.find(_.competitorId.isEmpty).map(_.withCompetitorId(cmpId))).getOrElse(
            CompScore().withCompetitorId(cmpId).withScore(createEmptyScore).withOrder(getMinUnusedOrder(scores, index))
              .withParentReferenceType(FightReferenceType.PROPAGATED)
          )
      })
    }

  }

  def removeCompetitorsFromGroup(
    groupFights: Map[String, FightDescription],
    change: CompetitorMovedToGroup,
    groupDescriptor: GroupDescriptor
  ): Map[String, FightDescription] = {
    val actualGroupSize = groupFights.values.toList.flatMap(_.scores)
      .distinctBy(s => s.competitorId.orElse(s.placeholderId)).size

    if (actualGroupSize <= groupDescriptor.size) {
      groupFights.map { case (k, it) =>
        k -> it.withScores(it.scores.map(sc => {
          if (sc.competitorId.contains(change.competitorId)) { sc.clearCompetitorId.withScore(createEmptyScore) }
          else { sc }
        }))
      }
    } else {
      groupFights.filter { case (_, fight) =>
        !fight.scores.exists(sc => sc.competitorId.contains(change.competitorId))
      }
    }
  }

  def addCompetitorToGroup(
    groupFights: Map[String, FightDescription],
    change: CompetitorMovedToGroup
  ): Map[String, FightDescription] = {
    if (groupFights.exists(e => e._2.scores.exists(_.competitorId.contains(change.competitorId)))) { groupFights }
    else {
      val flatScores    = groupFights.values.flatMap(_.scores)
      val placeholderId = flatScores.find(it => it.competitorId.isEmpty && it.placeholderId.isDefined)
      placeholderId match {
        case Some(value) =>
          // found a placeholder, it means there are already generated empty fights for this placeholder, update all the fights with this placeholder
          val updatedFights = groupFights.values.map(fight =>
            fight.id -> fight.withScores(fight.scores.map(s =>
              if (s.placeholderId == value.placeholderId) s.withCompetitorId(change.competitorId) else s
            ))
          )
          groupFights ++ updatedFights
        case None =>
          // did not find a placeholder -> need to generate new fights with each of the existing competitors in the group.
          val groupCompetitors   = flatScores.toList.mapFilter(_.competitorId).toSet
          val newCompetitorPairs = for { cmp <- groupCompetitors } yield (change.competitorId, cmp)

          val firstFreeNumberInRound = groupFights.values.map(_.numberInRound).max
          val extractor =
            (fight: FightDescription) => (fight.duration, fight.stageId, fight.competitionId, fight.categoryId)
          val (duration, stageId, competitionId, categoryId) = extractor(groupFights.values.head)

          val newPlaceholderId = flatScores.filter(cs => cs.competitorId.isDefined)
            .map(it => it.competitorId.get -> it.placeholderId).toMap +
            (change.competitorId -> Some(s"placeholder-${UUID.randomUUID()}"))

          val newFights = newCompetitorPairs.zipWithIndex.map { case ((competitor1, competitor2), index) =>
            fightDescription(
              competitionId,
              categoryId,
              stageId,
              0,
              StageRoundType.GROUP,
              firstFreeNumberInRound + index,
              duration,
              s"Round 0 fight ${firstFreeNumberInRound + index}",
              Option(change.groupId)
            ).withScores(Seq(
              createCompscoreForGroup(Option(competitor1), newPlaceholderId(competitor1), 0),
              createCompscoreForGroup(Option(competitor2), newPlaceholderId(competitor2), 1)
            ))
          }
          groupFights ++ newFights.map(f => (f.id, f))
      }
    }
  }
}
