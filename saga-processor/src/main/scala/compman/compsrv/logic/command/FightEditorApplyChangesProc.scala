package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, FightEditorApplyChangesCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.commands.payload.{CompetitorMovedToGroup, FightEditorApplyChangesPayload, GroupChangeType}
import compman.compsrv.model.dto.brackets.{BracketType, GroupDescriptorDTO, StageDescriptorDTO}
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.events.payload.FightEditorChangesAppliedPayload

object FightEditorApplyChangesProc /*{
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
        stageExists = state
          .stages
          .exists(_.exists(s => s.getId == payload.getStageId))
        event <-
          if (!stageExists) {
            EitherT.fromEither(
              Left[Errors.Error, EventDTO](
                Errors.StageDoesNotExist(payload.getStageId)
              )
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

  val changePriority: Map[GroupChangeType, Int] = GroupChangeType.values().map(it => {
    it -> (it match {
      case GroupChangeType.REMOVE => 0
      case GroupChangeType.ADD => 1
      case _ => Int.MaxValue
    })
  }).toMap.withDefault(_ => Int.MaxValue)


  def doSmth(stages: Seq[StageDescriptorDTO], payload: FightEditorApplyChangesPayload, fights: Seq[FightDescriptionDTO], competitionId: String, categoryId: String) = {
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