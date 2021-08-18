package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.service.FightsService
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, FightEditorApplyChangesCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.commands.payload.{
  CompetitorMovedToGroup,
  FightEditorApplyChangesPayload,
  FightsCompetitorUpdated,
  GroupChangeType
}
import compman.compsrv.model.dto.brackets.{BracketType, GroupDescriptorDTO, StageRoundType}
import compman.compsrv.model.dto.competition.{CompScoreDTO, FightDescriptionDTO}
import compman.compsrv.model.events.payload.FightEditorChangesAppliedPayload

import java.util.UUID

object FightEditorApplyChangesProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ FightEditorApplyChangesCommand(_, _, _) =>
      process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: FightEditorApplyChangesCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, NoPayloadError())
        stageExists = state.stages.flatMap(_.get(payload.getStageId)).isDefined
        event <-
          if (!stageExists) {
            EitherT.fromEither(
              Left[Errors.Error, EventDTO](Errors.StageDoesNotExist(payload.getStageId))
            )
          } else {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(new FightEditorChangesAppliedPayload)
              )
            )
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

  private def createPayload(payload: FightEditorApplyChangesPayload, state: CompetitionState) = {
    for {
      allStageFights <- state.fights.map(_.filter(_._2.getStageId == payload.getStageId))
      stages         <- state.stages
      stage          <- stages.get(payload.getStageId)
      bracketsType = stage.getBracketType
      a =
        bracketsType match {
          case BracketType.GROUP =>
            val groups          = stage.getGroupDescriptors
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
    } yield a
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
                  .setScore(FightsService.createEmptyScore)
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
        .flatMap(f => Option(f.getScores).getOrElse(Array.empty))
        .toList
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
                  sc.setCompetitorId(null).setScore(FightsService.createEmptyScore)
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
              FightsService
                .fightDescription(
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
                    FightsService.createCompscore(competitor1, newPlaceholderId(competitor1), 0),
                    FightsService.createCompscore(competitor2, newPlaceholderId(competitor2), 1)
                  )
                )
            })
          groupFights ++ newFights.map(f => (f.getId, f))
      }
    }
  }

}

/*def doSmth(stages: Seq[StageDescriptorDTO], payload: FightEditorApplyChangesPayload, fights: Seq[FightDescriptionDTO], competitionId: String, categoryId: String) = {
    val version = version()
    val stage = stages.find(_.getId == payload.getStageId).get
    val bracketsType = stage.getBracketType
    val allStageFights = fights.filter(_.getStageId == payload.getStageId)
    val stageFights = bracketsType match {
      case BracketType.GROUP =>
        val fightsByGroupId = allStageFights.groupBy(_.getGroupId)
        val competitorChangesByGroupId = payload.getCompetitorMovedToGroups.groupBy(_.getGroupId)
        val groups = stage.getGroupDescriptors
        groups.flatMap(gr => {
          val groupChanges = competitorChangesByGroupId.getOrElse(gr.getId, Array.empty)
          val groupFights = fightsByGroupId.getOrElse(gr.getId, Seq.empty)
          groupChanges.sortBy( it => {
            changePriority(it.getChangeType)
          })
            .foldLeft(groupFights)((acc, ch) =>
              ch.getChangeType match {
                case GroupChangeType.ADD =>
                  createUpdatesWithAddedCompetitor(
                    acc,
                    ch,
                    payload.getStageId,
                    competitionId,
                    categoryId
                  )
                case GroupChangeType.REMOVE =>
                  createUpdatesWithRemovedCompetitor(acc, ch, gr)
              }
            )
        }).sortedBy(it => it)
          .mapIndexed { i, fightDescriptionDTO -> fightDescriptionDTO.setNumberInRound(i) }
      case _ => {
        allStageFights.map { f ->
          payload.bracketsChanges.find { change -> change.fightId == f.id }?.let { change ->
          if (change.competitors.isNullOrEmpty()) {
            f.setScores(emptyArray())
          } else {
            val scores = f.scores.orEmpty()
            f.setScores(change.competitors.mapIndexed { index, cmpId ->
              scores.find { s -> s.competitorId == cmpId }
              ?: scores.find { s -> s.competitorId.isNullOrBlank() }?.setCompetitorId(cmpId)
              ?: CompScoreDTO()
                .setCompetitorId(cmpId)
                .setScore(ScoreDTO(0, 0, 0, emptyArray()))
                .setOrder(getMinUnusedOrder(scores, index))
            }.toTypedArray())
          }
        } ?: f
        }
      }
    }

    val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE },
      { it.setStatus(FightStatus.PENDING) })

    val clearedStageFights =
      clearAffectedFights(dirtyStageFights, payload.bracketsChanges.map { it.fightId }.toSet())

    val markedStageFights =
      FightsService.markAndProcessUncompletableFights(clearedStageFights, stage.dto.stageStatus) { id ->
        (allStageFights.firstOrNull { it.id == id }
          ?: stageFights.firstOrNull { it.id == id })?.scores?.toList()
      }

    return Category.createFightEditorChangesAppliedEvents(c,
      markedStageFights.filter { allStageFights.none { asf -> asf.id == it.id } },
      markedStageFights.filter { allStageFights.any { asf -> asf.id == it.id } },
      allStageFights.filter { asf -> markedStageFights.none { msf -> asf.id == msf.id } }
        .map { it.id }, createEvent
    ).map(this::enrichWithVersionAndNumber.curry()(version))
  }

  def createUpdatesWithAddedCompetitor(groupFights: Seq[FightDescriptionDTO], ch: CompetitorMovedToGroup, stageId: String,
                                       competitionId: String, categoryId: String): Seq[FightDescriptionDTO] = {
    if (groupFights.exists( f => f.getScores.exists(it => it.getCompetitorId == ch.getCompetitorId) )) {
            return groupFights
    }
    //find placeholder in existing fights.
    val flatScores = groupFights.flatMap(_.getScores)
    val placeholderId = flatScores.find(it => it.getCompetitorId == null && it.getPlaceholderId != null).map(_.getPlaceholderId)
    if (placeholderId.isDefined) {
    groupFights.map (fight =>
    fight.setScores(Option(fight.getScores).map(_.map(it => {
      if (it.getPlaceholderId == placeholderId.orNull) {
        it.setCompetitorId(ch.getCompetitorId)
      } else {
        it
      }
    })).getOrElse(Array.empty)))
  } else {
    val groupCompetitors = flatScores.map(_.getCompetitorId).distinct
    val newCompetitorPairs = GroupStageGenerateService.createPairs(groupCompetitors, listOf(ch.competitorId))
      .filter<Tuple2<String, String>> { it.a != it.b }.distinctBy { sortedSetOf<String?>(it.a, it.b).joinToString() }
    val startIndex = (groupFights.maxByOrNull { it.numberInRound }?.numberInRound
    ?: 0) + 1
    val duration = groupFights.first().duration
    val newPlaceholderId = (flatScores.map { it.competitorId to it.placeholderId } +
    (ch.competitorId to "placeholder-${IDGenerator.uid()}")).toMap<String?, String?>()
    val newFights = newCompetitorPairs.mapIndexed { index, tuple2 ->
    FightsService.fightDescription(competitionId, categoryId,
    stageId, 0,
    StageRoundType.GROUP, startIndex + index,
    duration, "Round 0 fight ${startIndex + index}", ch.groupId)
    .setScores(arrayOf(
    GroupStageGenerateService.createCompscore(tuple2.a, newPlaceholderId[tuple2.a], 0),
    GroupStageGenerateService.createCompscore(tuple2.b, newPlaceholderId[tuple2.b], 1)))
  }
    groupFights + newFights
  }
  }

  def createUpdatesWithRemovedCompetitor(groupFights: Seq[FightDescriptionDTO], ch: CompetitorMovedToGroup, groupDescriptorDTO: GroupDescriptorDTO): List[FightDescriptionDTO] = {
    val actualGroupSize = groupFights.flatMap { it.scores.orEmpty().toList() }.distinctBy {
    it.competitorId ?: it.placeholderId
  }.size
    return if (actualGroupSize <= groupDescriptorDTO.size) {
    groupFights.map {
    it.setScores(it.scores?.map { sc ->
    if (sc.competitorId == ch.competitorId) {
    sc.setCompetitorId(null).setScore(FightsService.createEmptyScore())
  } else {
    sc
  }
  }?.toTypedArray())
  }
  } else {
    groupFights.filter {
    it.scores?.none { sc -> sc.competitorId == ch.competitorId } ?: true
  }
  }
  }

}
 */
