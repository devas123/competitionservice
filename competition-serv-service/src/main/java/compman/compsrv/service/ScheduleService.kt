package compman.compsrv.service

import arrow.core.Tuple3
import com.compmanager.service.ServiceException
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.service.schedule.BracketSimulatorFactory
import compman.compsrv.service.schedule.IBracketSimulator
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.compNotEmpty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
class ScheduleService(private val bracketSimulatorFactory: BracketSimulatorFactory) {

    companion object {
        fun obsoleteFight(f: FightDescriptionDTO, threeCompetitorCategory: Boolean = false): Boolean {
            if (threeCompetitorCategory) {
                return false
            }
            if ((f.parentId1 != null) || (f.parentId2 != null)) return false
            return !(f.scores != null && f.scores.size == 2 && f.scores.all { compNotEmpty(it.competitor) })
        }
    }

    private data class InternalFightStartTime(val fight: FightDescriptionDTO,
                                              val matId: String,
                                              val fightNumber: Int,
                                              val startTime: Instant,
                                              val periodId: String)

    private data class InternalMatScheduleContainer(
            val currentTime: Instant,
            val totalFights: Int,
            val id: String,
            val fights: List<InternalFightStartTime>,
            val timeZone: String,
            val pending: List<FightDescriptionDTO>)

    private class ScheduleComposer(val startTime: Instant, val numberOfMats: Int, brackets: List<IBracketSimulator>, val pause: BigDecimal, riskFactor: BigDecimal, val periodId: String, val timeZone: String) {
        val schedule: MutableList<ScheduleEntryDTO>
        val riskCoeff: BigDecimal
        val brackets: ArrayList<IBracketSimulator> = ArrayList(brackets)

        init {
            this.schedule = ArrayList()

            riskCoeff = BigDecimal.ONE.plus(riskFactor)
        }

        fun categoryNotRegistered(categoryId: String): Boolean {
            return this.schedule.size == 0 || !this.schedule.any { it.categoryId == categoryId }
        }

        fun updateSchedule(f: FightDescriptionDTO, startTime: Instant) {
            if (this.categoryNotRegistered(f.categoryId)) {
                this.schedule.add(ScheduleEntryDTO(f.categoryId,
                        startTime,
                        1,
                        f.duration ?: BigDecimal.ZERO))
            } else {
                val entry = this.schedule.first { it.categoryId == f.categoryId }
                entry.numberOfFights += 1
            }
        }


        fun acceptFight(fightsByMats: List<InternalMatScheduleContainer>, f: FightDescriptionDTO, duration: BigDecimal, lastrun: Boolean): List<InternalMatScheduleContainer> {
            fun updateMatInCollection(freshMatCopy: InternalMatScheduleContainer) =
                    fightsByMats.map {
                        if (it.id == freshMatCopy.id) {
                            freshMatCopy
                        } else {
                            it
                        }
                    }

            val freshMat = fightsByMats.find { it.fights.isEmpty() }
            return if (freshMat != null) {
                val currentTime = ZonedDateTime.ofInstant(freshMat.currentTime, ZoneId.of(timeZone))
                this.updateSchedule(f, currentTime.toInstant())
                updateMatInCollection(
                        freshMat.copy(currentTime = currentTime.plusMinutes(duration.toLong()).toInstant(),
                                fights = freshMat.fights + InternalFightStartTime(fight = f,
                                        fightNumber = freshMat.totalFights + 1,
                                        startTime = currentTime.toInstant(),
                                        matId = freshMat.id, periodId = periodId))
                )
            } else {
                if (this.categoryNotRegistered(f.categoryId)) {
                    val mat = fightsByMats.minBy { a -> a.currentTime.toEpochMilli() }!!
                    val currentTime = mat.currentTime
                    this.updateSchedule(f, currentTime)
                    updateMatInCollection(
                            mat.copy(currentTime = currentTime.plus(Duration.ofMinutes(duration.toLong())),
                                    fights = mat.fights +
                                            InternalFightStartTime(fight = f,
                                                    fightNumber = mat.totalFights + 1,
                                                    startTime = currentTime,
                                                    periodId = periodId,
                                                    matId = mat.id))
                    )
                } else {
                    val mat: InternalMatScheduleContainer
                    val matsWithTheSameCategory = fightsByMats
                            .filter { m ->
                                m.fights.isNotEmpty() && m.fights.last().fight.categoryId == f.categoryId
                                        && (m.fights.last().fight.round ?: -1) < (f.round ?: -1)
                            }
                    if (matsWithTheSameCategory.isNotEmpty() && !lastrun) {
                        mat = matsWithTheSameCategory.minBy { it.currentTime.toEpochMilli() }!!
                        updateMatInCollection(mat.copy(pending = mat.pending + f))
                    } else {
                        mat = fightsByMats.minBy { it.currentTime.toEpochMilli() }!!
                        val currentTime = mat.currentTime
                        this.updateSchedule(f, currentTime)
                        updateMatInCollection(mat.copy(currentTime = currentTime.plus(Duration.ofMinutes(duration.toLong())),
                                fights = mat.fights + InternalFightStartTime(
                                        fight = f, fightNumber = mat.totalFights + 1, startTime = currentTime,
                                        matId = mat.id, periodId = periodId)))
                    }
                }
            }

        }

        fun getFightDuration(fight: FightDescriptionDTO) = fight.duration!!.multiply(riskCoeff).plus(this.pause)

        fun simulate(): List<InternalMatScheduleContainer> {
            val activeBrackets = ArrayList<IBracketSimulator>()
            var fightsByMats = (0 until numberOfMats).map { i ->
                val initDate = ZonedDateTime.ofInstant(startTime, ZoneId.of(timeZone))
                InternalMatScheduleContainer(
                        timeZone = timeZone,
                        id = IDGenerator.createMatId(periodId, i),
                        fights = emptyList(),
                        currentTime = initDate.toInstant(),
                        totalFights = 0,
                        pending = emptyList())
            }


            while (this.brackets.isNotEmpty() || activeBrackets.isNotEmpty()) {
                val fights = ArrayList<FightDescriptionDTO>()
                var i = 0
                if (activeBrackets.getOrNull(i) != null) {
                    fights.addAll(activeBrackets[i++].getNextRound())
                }
                while (fights.size <= this.numberOfMats && this.brackets.isNotEmpty()) {
                    if (activeBrackets.getOrNull(i) == null) {
                        activeBrackets.add(this.brackets.removeAt(0))
                    }
                    fights.addAll(activeBrackets[i++].getNextRound())
                }
                activeBrackets.removeIf { b -> b.isEmpty() }
                fightsByMats = fights.fold(fightsByMats) {acc, f ->
                    this.acceptFight(acc, f, getFightDuration(f), false)
                }
            }
            val pendingFights = fightsByMats.flatMap { it.pending }
            return pendingFights.fold(fightsByMats) { acc, fight ->
                this.acceptFight(acc, fight, getFightDuration(fight), true)
            }
        }
    }

    fun generateSchedule(properties: SchedulePropertiesDTO, stages: List<Pair<StageDescriptorDTO, List<FightDescriptionDTO>>>, timeZone: String): ScheduleDTO {
        if (!properties.periodPropertiesList.isNullOrEmpty()) {
            if (stages.flatMap { it.second }.isEmpty()) {
                throw ServiceException("No fights generated.")
            }
            return doGenerateSchedule(stages, properties, timeZone)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(stages: List<Pair<StageDescriptorDTO, List<FightDescriptionDTO>>>, properties: SchedulePropertiesDTO, timeZone: String): ScheduleDTO {
        return ScheduleDTO()
                .setId(properties.competitionId)
                        .setPeriods(properties.periodPropertiesList?.mapNotNull { periodPropertiesDTO ->
                            periodPropertiesDTO?.let { p ->
                        val id = IDGenerator.createPeriodId(properties.competitionId)
                        val periodStartTime = p.startTime
                        val brackets = p.categories.filter { !it.competitors.isNullOrEmpty() }
                                .flatMap { cat ->
                                    stages.filter { st -> st.first.categoryId == cat.id }
                                            .map { Tuple3(cat, it.first, it.second) }  }
                                .map { tuple3 ->
                                    bracketSimulatorFactory.createSimulator(tuple3.b.id!!, tuple3.b.categoryId, tuple3.c,
                                      tuple3.b.bracketType, tuple3.a.competitors?.size ?: 0)
                                }
                        val composer = ScheduleComposer(periodStartTime, p.numberOfMats, brackets, BigDecimal(p.timeBetweenFights), p.riskPercent, id, timeZone)
                        val fightsByMats = composer.simulate()
                        PeriodDTO()
                                .setId(id)
                                .setSchedule(composer.schedule.toTypedArray())
                                .setFightsByMats(fightsByMats.map { container ->
                                    MatScheduleContainerDTO()
                                        .setFights(container.fights.map { FightStartTimePairDTO()
                                                .setStartTime(it.startTime)
                                                .setFightNumber(it.fightNumber)
                                                .setFightId(it.fight.id)
                                                .setPeriodId(it.periodId)
                                                .setFightCategoryId(it.fight.categoryId)
                                                .setMatId(it.matId)}.toTypedArray())
                                        .setId(container.id)
                                        .setTimeZone(container.timeZone)
                                        .setTotalFights(container.totalFights)}.toTypedArray())
                                .setStartTime(periodStartTime)
                                .setName(p.name)
                                .setNumberOfMats(p.numberOfMats)
                    }
                }?.toTypedArray())
                .setScheduleProperties(properties)
    }
}