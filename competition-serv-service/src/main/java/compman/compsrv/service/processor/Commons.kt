package compman.compsrv.service.processor

import arrow.core.Either
import arrow.core.Tuple2
import arrow.core.right
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.CompetitorGroupChange
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.brackets.GroupDescriptorDTO
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.fight.GroupStageGenerateService
import compman.compsrv.util.IDGenerator
import org.slf4j.LoggerFactory

typealias AggregateWithEvents<EntityType> = Pair<EntityType?, List<EventDTO>>
typealias CommandExecutor<EntityType> = (EntityType?, DBOperations, CommandDTO) -> AggregateWithEvents<EntityType>
typealias CreateEvent = (CommandDTO, EventType, Payload) -> EventDTO

fun executeInAppropriateService(command: CommandDTO, rocksDBOperations: DBOperations, delegatingAggregateService: DelegatingAggregateService): AggregateWithEvents<AbstractAggregate> {
    return delegatingAggregateService.getAggregateService(command).processCommand(command, rocksDBOperations)
}


private val log = LoggerFactory.getLogger("compman.compsrv.service.processor.command.Commons")

fun createUpdatesWithAddedCompetitor(groupFights: List<FightDescriptionDTO>, ch: CompetitorGroupChange, stageId: String,
                                             competitionId: String, categoryId: String): List<FightDescriptionDTO> {
    if (groupFights.any { f -> f.scores.any { it.competitorId == ch.competitorId } }) {
        log.info("Group already contains fights for competitor ${ch.competitorId}. Assuming the competitor is already added to the group.")
        return groupFights
    }
    //find placeholder in existing fights.
    val flatScores = groupFights.flatMap { it.scores.orEmpty().toList() }
    val placeholderId = flatScores
            .find { it.competitorId.isNullOrBlank() && !it.placeholderId.isNullOrBlank() }?.placeholderId
    return if (!placeholderId.isNullOrBlank()) {
        log.info("Found placeholder: $placeholderId")
        groupFights.map { fight ->
            fight.setScores(fight.scores?.map {
                if (it.placeholderId == placeholderId) {
                    log.info("Updating fight with compscores: ${it.competitorId}/${it.placeholderId}, setting competitorId to ${ch.competitorId}")
                    it.setCompetitorId(ch.competitorId)
                } else {
                    it
                }
            }?.toTypedArray())
        }
    } else {
        log.info("Did not find placeholder, creating new fight.")
        val groupCompetitors = flatScores.map { it.competitorId }.distinct()
        val newCompetitorPairs = GroupStageGenerateService.createPairs(groupCompetitors, listOf(ch.competitorId))
                .filter<Tuple2<String, String>> { it.a != it.b }.distinctBy { sortedSetOf<String?>(it.a, it.b).joinToString() }
        val startIndex = (groupFights.maxBy { it.numberInRound }?.numberInRound
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

fun createUpdatesWithRemovedCompetitor(groupFights: List<FightDescriptionDTO>, ch: CompetitorGroupChange, groupDescriptorDTO: GroupDescriptorDTO): List<FightDescriptionDTO> {
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

data class LabeledFight(val fight: Either<Unit, FightDescriptionDTO>, val label: String, val id: Either<Unit, String> = Either.left(Unit)) {
    constructor(fight: FightDescriptionDTO, label: String) : this(fight.right(), label, Either.left(Unit))
    companion object {
        const val UPDATED = "UPDATED"
        const val NEW = "NEW"
        const val REMOVED = "REMOVED"
    }
}

