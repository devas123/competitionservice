package compman.compsrv.logic.command

import cats.Monad
import cats.data.{EitherT, OptionT}
import cats.implicits._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.service.fights._
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, FightEditorApplyChangesCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.commands.payload.{CompetitorMovedToGroup, FightEditorApplyChangesPayload, FightsCompetitorUpdated, GroupChangeType}
import compman.compsrv.model.dto.brackets.{BracketType, GroupDescriptorDTO, StageDescriptorDTO, StageRoundType}
import compman.compsrv.model.dto.competition.{CompScoreDTO, FightDescriptionDTO, FightStatus}
import compman.compsrv.model.events.payload.FightEditorChangesAppliedPayload
import compman.compsrv.model.extension._

import java.util.UUID
import scala.annotation.tailrec

object FightEditorApplyChangesProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x@FightEditorApplyChangesCommand(_, _, _) =>
      process[F](x, state)
  }

  private def process[F[+_] : Monad : IdOperations : EventOperations](
                                                                       command: FightEditorApplyChangesCommand,
                                                                       state: CompetitionState
                                                                     ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
        stageExists = state.stages.flatMap(_.get(payload.getStageId)).isDefined
        event <-
          if (!stageExists) {
            EitherT.fromEither[F](
              Left[Errors.Error, EventDTO](Errors.StageDoesNotExist(payload.getStageId))
            )
          } else {
            for {
              eventPayload <- EitherT.liftF[F, Errors.Error, Option[FightEditorChangesAppliedPayload]](createPayload[F](payload, state))
              evt <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = eventPayload
              ))
            } yield evt
          }
      } yield Seq(event)
    eventT.value
  }

  val changePriority: Map[GroupChangeType, Int] = GroupChangeType
    .values()
    .map(it => {
      it ->
        (it match {
          case GroupChangeType.REMOVE =>
            0
          case GroupChangeType.ADD =>
            1
          case _ =>
            Int.MaxValue
        })
    })
    .toMap
    .withDefault(_ => Int.MaxValue)

  private def createPayload[F[+_] : Monad](payload: FightEditorApplyChangesPayload, state: CompetitionState) = {
    (for {
      updates <- OptionT.fromOption[F](createUpdates(payload, state))
      allFights <- OptionT.fromOption[F](state.fights)
      bracketChanges <- OptionT.fromOption[F](Option(payload.getBracketsChanges))
      cleanedUpdates = updates.map(f => f._1 -> (if (f._2.getStatus == FightStatus.UNCOMPLETABLE) f._2.setStatus(FightStatus.PENDING) else f._2))
      cleanedAffected = clearAffectedFights(cleanedUpdates, bracketChanges.map(_.getFightId).toSet)
      markedFights <- OptionT.liftF(FightUtils.markAndProcessUncompletableFights[F](cleanedAffected))
      updates = markedFights.filter(f => allFights.contains(f._1))
      additions = markedFights.filter(f => !allFights.contains(f._1))
      removals = allFights.filter(f => f._2.getStageId == payload.getStageId && !markedFights.contains(f._1))
    } yield new FightEditorChangesAppliedPayload().setNewFights(additions.values.toArray).setUpdates(updates.values.toArray).setRemovedFighids(removals.keys.toArray)
      ).value
  }

  @tailrec
  private def clearAffectedFights(
                                   fights: Map[String, FightDescriptionDTO],
                                   changedIds: Set[String]
                                 ): Map[String, FightDescriptionDTO] = {
    if (changedIds.isEmpty) {
      fights
    } else {
      val affectedFights =
        fights.filter(fg => {
          fg._2.getScores.exists(s => {
            changedIds.contains(s.getParentFightId)
          })
        })
      clearAffectedFights(fights.map(f => {
        f._1 -> f._2.setScores(
          f._2.getScores.map(it => if (changedIds.contains(it.getParentFightId))
            it.setCompetitorId(null)
          else it)
        )
      }), affectedFights.keySet)
    }
  }

  private def createUpdates(payload: FightEditorApplyChangesPayload, state: CompetitionState) = {
    for {
      allStageFights <- state.fights.map(_.filter(_._2.getStageId == payload.getStageId))
      stages <- state.stages
      stage <- stages.get(payload.getStageId)
      bracketsType = stage.getBracketType
      a = applyChanges(payload, allStageFights, stage, bracketsType)
    } yield a
  }

  private def applyChanges(payload: FightEditorApplyChangesPayload, allStageFights: Map[String, FightDescriptionDTO], stage: StageDescriptorDTO, bracketsType: BracketType) = {
    bracketsType match {
      case BracketType.GROUP =>
        val groups = stage.getGroupDescriptors
        val fightsByGroupId = allStageFights.values.groupBy(_.getGroupId)
        val competitorChangesByGroupId = payload
          .getCompetitorMovedToGroups
          .groupBy(_.getGroupId)
        groups
          .flatMap(gr => {
            val groupChanges = competitorChangesByGroupId.getOrElse(gr.getId, Array.empty)
            val groupFights =
              fightsByGroupId.getOrElse(gr.getId, Iterable.empty).map(f => (f.getId, f)).toMap
            groupChanges
              .sortBy(c => changePriority(c.getChangeType))
              .foldLeft(groupFights)((acc, ch) => {
                ch.getChangeType match {
                  case GroupChangeType.ADD =>
                    addCompetitorToGroup(groupFights, ch)
                  case GroupChangeType.REMOVE =>
                    removeCompetitorsFromGroup(acc, ch, gr)
                }

              })
          })
          .sortBy(it => {
            it._2.getNumberInRound
          })
          .zipWithIndex
          .map(z => {
            z._1._2.setNumberInRound(z._2)
          }).map(f => (f.getId, f)).toMap
      case _ =>
        updateEliminationBrackets(allStageFights, payload.getBracketsChanges)
    }
  }

  private def getMinUnusedOrder(scores: Array[CompScoreDTO], index: Int = 0): Int = {
    if (scores == null || scores.isEmpty) {
      0
    } else {
      (0 to scores.length + index).filter(i => {
        !scores.exists(s => s.getOrder == i)
      })(index)
    }
  }

  private def updateEliminationBrackets(
      allStageFights: Map[String, FightDescriptionDTO],
      bracketChanges: Array[FightsCompetitorUpdated]
  ) = {
    allStageFights.map(f => {
      f._1 ->
        bracketChanges
          .find(change => change.getFightId == f._1)
          .map(change => applyChangeToFight(f._2, change))
          .getOrElse(f._2)
    })
  }

  private def applyChangeToFight(f: FightDescriptionDTO, change: FightsCompetitorUpdated) = {
    if (change.getCompetitors == null || change.getCompetitors.isEmpty) {
      f.setScores(Array.empty)
    } else {
      val scores = Option(f.getScores).getOrElse(Array.empty)
      f.setScores(
        change
          .getCompetitors
          .zipWithIndex
          .map(z => {
            val (cmpId, index) = z
            scores
              .find(s => {
                s.getCompetitorId == cmpId
              })
              .orElse(scores.find(s => s.getCompetitorId == null).map(_.setCompetitorId(cmpId)))
              .getOrElse(
                new CompScoreDTO()
                  .setCompetitorId(cmpId)
                  .setScore(createEmptyScore)
                  .setOrder(getMinUnusedOrder(scores, index))
              )
          })
      )
    }

  }

  def removeCompetitorsFromGroup(
      groupFights: Map[String, FightDescriptionDTO],
      change: CompetitorMovedToGroup,
      groupDescriptorDTO: GroupDescriptorDTO
  ): Map[String, FightDescriptionDTO] = {
    val actualGroupSize =
      groupFights
        .values
        .toList
        .mapFilter(_.scores)
        .flatten
        .distinctBy(s => Option(s.getCompetitorId).getOrElse(s.getPlaceholderId))
        .size

    if (actualGroupSize <= groupDescriptorDTO.getSize) {
      groupFights.map(e => {
        val (k, it) = e
        k ->
          it.setScores(
            it.getScores
              .map(sc => {
                if (sc.getCompetitorId == change.getCompetitorId) {
                  sc.setCompetitorId(null).setScore(createEmptyScore)
                } else {
                  sc
                }
              })
          )
      })
    } else {
      groupFights
        .filter(it => !it._2.getScores.exists(sc => sc.getCompetitorId == change.getCompetitorId))
    }
  }

  def addCompetitorToGroup(
      groupFights: Map[String, FightDescriptionDTO],
      change: CompetitorMovedToGroup
  ): Map[String, FightDescriptionDTO] = {
    if (
      groupFights.exists(e =>
        e._2.getScores != null && e._2.getScores.exists(_.getCompetitorId == change.getCompetitorId)
      )
    ) {
      groupFights
    } else {
      val flatScores = groupFights
        .values
        .flatMap(it => {
          Option(it.getScores).getOrElse(Array.empty)
        })
      val placeholderId = flatScores.find(it => {
        it.getCompetitorId == null && it.getPlaceholderId != null
      })
      placeholderId match {
        case Some(value) =>
          // found a placeholder, it means there are already generated empty fights for this placeholder, update all the fights with this placeholder
          val updatedFights = groupFights
            .values
            .map(fight =>
              fight.getId ->
                fight.setScores(
                  fight
                    .getScores
                    .map(s =>
                      if (s.getPlaceholderId == value.getPlaceholderId)
                        s.setCompetitorId(change.getCompetitorId)
                      else
                        s
                    )
                )
            )
          groupFights ++ updatedFights
        case None =>
          // did not find a placeholder -> need to generate new fights with each of the existing competitors in the group.
          val groupCompetitors = flatScores.map(_.getCompetitorId).toSet
          val newCompetitorPairs =
            for {
              cmp <- groupCompetitors
            } yield (change.getCompetitorId, cmp)

          val firstFreeNumberInRound = groupFights.values.map(_.getNumberInRound).max
          val extractor =
            (fight: FightDescriptionDTO) =>
              (fight.getDuration, fight.getStageId, fight.getCompetitionId, fight.getCategoryId)
          val (duration, stageId, competitionId, categoryId) = extractor(groupFights.values.head)

          val newPlaceholderId =
            flatScores.map(it => it.getCompetitorId -> it.getPlaceholderId).toMap +
              (change.getCompetitorId -> s"placeholder-${UUID.randomUUID()}")

          val newFights = newCompetitorPairs
            .zipWithIndex
            .map(arg => {
              val (tuple2, index) = arg
              val competitor1     = tuple2._1
              val competitor2     = tuple2._2
                fightDescription(
                  competitionId,
                  categoryId,
                  stageId,
                  0,
                  StageRoundType.GROUP,
                  firstFreeNumberInRound + index,
                  duration,
                  "Round 0 fight ${startIndex + index}",
                  change.getGroupId
                )
                .setScores(
                  Array(
                    createCompscore(Option(competitor1), Option(newPlaceholderId(competitor1)), 0),
                    createCompscore(Option(competitor2), Option(newPlaceholderId(competitor2)), 1)
                  )
                )
            })
          groupFights ++ newFights.map(f => (f.getId, f))
      }
    }
  }

}