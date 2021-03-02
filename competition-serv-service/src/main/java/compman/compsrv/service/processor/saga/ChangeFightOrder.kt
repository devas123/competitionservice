package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.ChangeFightOrderPayload
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightPropertiesUpdate
import compman.compsrv.model.events.payload.FightPropertiesUpdatedPayload
import compman.compsrv.model.events.payload.MatsUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.*
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

@Component
class ChangeFightOrder(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>, override val delegatingAggregateService: DelegatingAggregateService
) : ISagaExecutor, ValidatedCommandExecutor<AbstractAggregate>(mapper, validators) {

    private fun process(
        payload: ChangeFightOrderPayload,
        c: CommandDTO,
        createEvent: CreateEvent,
        fights: Array<FightDescriptionDTO>,
        mats: Array<MatDescriptionDTO>
    ): SagaStep<List<EventDTO>> {
        val newOrderOnMat = max(payload.newOrderOnMat, 0)
        val fight = fights.first { it.id == payload.fightId }
        return when (fight.status) {
            FightStatus.IN_PROGRESS, FightStatus.FINISHED -> {
                throw IllegalArgumentException("Cannot move fight that is finished or in progress.")
            }
            else -> {
                val steps = mutableListOf<SagaStep<List<EventDTO>>>()
                val updates = mutableListOf<Pair<String, FightPropertiesUpdate>>()
                addNumberOfFightsChangedEvents(payload, steps, createEvent, mats, c)
                val duration = fight.duration.toLong()
                var startTime: Instant? = null
                var maxStartTime: Instant? = null
                if (payload.newMatId != payload.currentMatId) {
                    //if mats are different
                    for (f in fights) {
                        val (ms, sm) = updateStartTimes(f, payload, startTime, maxStartTime, newOrderOnMat)
                        maxStartTime = ms
                        startTime = sm
                        if (f.id != payload.fightId && f.mat.id == payload.currentMatId && f.numberOnMat != null && f.numberOnMat >= payload.currentOrderOnMat) {
                            //first reduce numbers on the current mat
                            updates.add(f.categoryId to FightPropertiesUpdate().setFightId(f.id).setMat(f.mat)
                                .setNumberOnMat(f.numberOnMat - 1)
                                .setStartTime(f.startTime.minus(duration, ChronoUnit.MINUTES)))
                        } else if (f.id != payload.fightId && f.mat.id == payload.newMatId && f.numberOnMat != null && f.numberOnMat >= payload.newOrderOnMat) {
                            updates.add(f.categoryId to
                                    FightPropertiesUpdate().setFightId(f.id).setMat(f.mat)
                                        .setNumberOnMat(f.numberOnMat + 1)
                                        .setStartTime(f.startTime.plus(duration, ChronoUnit.MINUTES)))
                        }
                    }
                } else {
                    //mats are the same
                    for (f in fights) {
                        val (ms, sm) = updateStartTimes(f, payload, startTime, maxStartTime, newOrderOnMat)
                        maxStartTime = ms
                        startTime = sm
                        if (f.id != payload.fightId && f.mat.id == payload.currentMatId && f.numberOnMat != null
                            && f.numberOnMat >= min(payload.currentOrderOnMat, payload.newOrderOnMat) &&
                            f.numberOnMat <= max(payload.currentOrderOnMat, payload.newOrderOnMat)
                        ) {
                            //first reduce numbers on the current mat
                            if (payload.currentOrderOnMat > payload.newOrderOnMat) {
                                updates.add(f.categoryId to FightPropertiesUpdate().setFightId(f.id).setMat(f.mat)
                                    .setNumberOnMat(f.numberOnMat + 1)
                                    .setStartTime(f.startTime.plus(duration, ChronoUnit.MINUTES)))
                            } else {
                                //update fight
                                updates.add(f.categoryId to FightPropertiesUpdate().setFightId(f.id).setMat(f.mat)
                                    .setNumberOnMat(f.numberOnMat - 1)
                                    .setStartTime(f.startTime.minus(duration, ChronoUnit.MINUTES)))
                            }
                        }
                    }
                }
                updates.add(fight.categoryId to FightPropertiesUpdate().setFightId(fight.id)
                    .setMat(mats.first { it.id == payload.newMatId })
                    .setStartTime(startTime ?: maxStartTime)
                    .setNumberOnMat(newOrderOnMat)
                )
                steps.addAll(updates.groupBy { it.first }.mapValues { e ->
                    val fightUpdates = e.value.map { it.second }
                    fightUpdates.chunked(100).map { list ->
                        applyEvent(Unit.left(),
                            createEvent(
                                c, EventType.FIGHT_PROPERTIES_UPDATED, FightPropertiesUpdatedPayload()
                                    .setUpdates(list.toTypedArray())
                            ).apply { categoryId = e.key }
                        )
                    }.reduce { a, b -> a + b }
                }.values)

                steps.reduce { a, b -> a + b }
            }
        }
    }

    private fun updateStartTimes(
        f: FightDescriptionDTO,
        payload: ChangeFightOrderPayload,
        startTime: Instant?,
        maxStartTime: Instant?,
        newOrderOnMat: Int
    ): Pair<Instant?, Instant?> {
        var startTime1 = startTime
        var maxStartTime1 = maxStartTime
        if (f.id != payload.fightId && f.mat.id == payload.newMatId && f.numberOnMat == newOrderOnMat) {
            startTime1 = f.startTime
        }
        if (f.id != payload.fightId && f.mat.id == payload.newMatId && (maxStartTime1 == null || maxStartTime1 < f.startTime)) {
            maxStartTime1 = f.startTime
        }
        return Pair(maxStartTime1, startTime1)
    }

    private fun addNumberOfFightsChangedEvents(
        payload: ChangeFightOrderPayload,
        steps: MutableList<SagaStep<List<EventDTO>>>,
        createEvent: CreateEvent,
        mats: Array<MatDescriptionDTO>,
        c: CommandDTO
    ) {
        if (payload.currentMatId != payload.newMatId) {
            val currentMat = mats.first { it.id == payload.currentMatId }
            val newMat = mats.first { it.id == payload.newMatId }
            if (payload.currentMatId != null) {
                val matsUpdates = arrayOf(
                    MatDescriptionDTO()
                        .setId(payload.currentMatId)
                        .setNumberOfFights(currentMat.numberOfFights - 1),
                    MatDescriptionDTO()
                        .setId(payload.newMatId)
                        .setNumberOfFights(newMat.numberOfFights + 1)
                )
                steps.add(
                    applyEvent(
                        Unit.left(), createEvent(
                            c,
                            EventType.MATS_UPDATED,
                            MatsUpdatedPayload().setMats(matsUpdates)
                        ).apply { matId = payload.currentMatId }
                    )
                )
            }
        }
    }

    override fun createSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, SagaStep<List<EventDTO>>> {
        val fights = dbOperations.getCompetitionFights(command.competitionId)
        val mats = dbOperations.getCompetition(command.competitionId).mats
        return createSaga<ChangeFightOrderPayload>(command) { payload, _ ->
            process(payload, command, AbstractAggregateService.Companion::createEvent, fights, mats)
        }
    }


    override val commandType: CommandType
        get() = CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND


}