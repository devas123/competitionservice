package compman.compsrv.service

import com.compmanager.service.ServiceException
import compman.compsrv.jpa.brackets.StageDescriptor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.jpa.schedule.*
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
import java.util.*
import kotlin.collections.ArrayList

@Component
class ScheduleService(private val bracketSimulatorFactory: BracketSimulatorFactory) {

    companion object {
        fun createPeriodId(competitionId: String) = IDGenerator.hashString("$competitionId-period-${UUID.randomUUID()}")
        fun createMatId(periodId: String, matNumber: Int) = IDGenerator.hashString("$periodId-mat-$matNumber")
        fun obsoleteFight(f: FightDescription, threeCompetitorCategory: Boolean = false): Boolean {
            if (threeCompetitorCategory) {
                return false
            }
            if ((f.parentId1 != null) || (f.parentId2 != null)) return false
            return !(f.scores?.size == 2 && (f.scores?.all { compNotEmpty(it.competitor) } == true))
        }
        fun hasCompetitors(f: FightDescription): Boolean {
            val scores = f.scores
            return scores?.size != null && scores.size > 0 && (f.scores?.any { compNotEmpty(it.competitor) } == true)
        }
    }

    private class ScheduleComposer(startTime: Instant, val numberOfMats: Int, brackets: List<IBracketSimulator>, val pause: BigDecimal, riskFactor: BigDecimal, periodId: String, val timeZone: String) {
        val schedule: MutableList<ScheduleEntry>
        val fightsByMats: ArrayList<MatScheduleContainer> = ArrayList(numberOfMats)
        val riskCoeff: BigDecimal
        val brackets: ArrayList<IBracketSimulator> = ArrayList(brackets)

        init {
            this.schedule = ArrayList()
            for (i in 0 until numberOfMats) {
                val initDate = ZonedDateTime.ofInstant(startTime, ZoneId.of(timeZone))
                fightsByMats.add(MatScheduleContainer(initDate.toInstant(), createMatId(periodId, i)))
            }
            riskCoeff = BigDecimal.ONE.plus(riskFactor)
        }

        fun categoryNotRegistered(categoryId: String): Boolean {
            return this.schedule.size == 0 || !this.schedule.any { it.categoryId == categoryId }
        }

        fun updateSchedule(f: FightDescription, startTime: Instant) {
            if (this.categoryNotRegistered(f.categoryId)) {
                this.schedule.add(ScheduleEntry(
                        categoryId = f.categoryId,
                        startTime = startTime,
                        numberOfFights = 1,
                        fightDuration = f.duration ?: BigDecimal.ZERO))
            } else {
                val entry = this.schedule.first { it.categoryId == f.categoryId }
                entry.numberOfFights += 1
            }
        }

        fun acceptFight(f: FightDescription, duration: BigDecimal, lastrun: Boolean) {
            val freshMat = this.fightsByMats.find { it.fights.isEmpty() }
            if (freshMat != null) {
                val currentTime = ZonedDateTime.ofInstant(freshMat.currentTime, ZoneId.of(timeZone))
                this.updateSchedule(f, currentTime.toInstant())
                freshMat.currentTime = currentTime.plusMinutes(duration.toLong()).toInstant()
                freshMat.fights += FightStartTimePair(f, freshMat.totalFights++, currentTime.toInstant())
            } else {
                if (this.categoryNotRegistered(f.categoryId)) {
                    val mat = this.fightsByMats.minBy { a -> a.currentTime.toEpochMilli() }!!
                    val currentTime = mat.currentTime
                    this.updateSchedule(f, currentTime)
                    mat.currentTime = currentTime.plus(Duration.ofMinutes(duration.toLong()))
                    mat.fights += FightStartTimePair(f, mat.totalFights++, currentTime)
                } else {
                    val mat: MatScheduleContainer
                    val matsWithTheSameCategory = this.fightsByMats
                            .filter { m ->
                                m.fights.isNotEmpty() && m.fights.last().fight.categoryId == f.categoryId && (m.fights.last().fight.round
                                        ?: -1) < (f.round ?: -1)
                            }
                    if (matsWithTheSameCategory.isNotEmpty() && !lastrun) {
                        mat = matsWithTheSameCategory.minBy { it.currentTime.toEpochMilli() }!!
                        mat.pending.add(f)
                    } else {
                        mat = this.fightsByMats.minBy { it.currentTime.toEpochMilli() }!!
                        val currentTime = mat.currentTime
                        this.updateSchedule(f, currentTime)
                        mat.currentTime = currentTime.plus(Duration.ofMinutes(duration.toLong()))
                        mat.fights += FightStartTimePair(f, mat.totalFights++, currentTime)
                        this.fightsByMats.forEach { m ->
                            if (m.pending.isNotEmpty()) {
                                val pendingFights = Array(m.pending.size) { index -> m.pending[index] }
                                m.pending.clear()
                                pendingFights.forEach { fight ->
                                    this.acceptFight(fight, getFightDuration(fight), lastrun)
                                }
                            }
                        }
                    }
                }
            }

        }

        fun getFightDuration(fight: FightDescription) = fight.duration!!.multiply(riskCoeff).plus(this.pause)

        fun simulate() {
            val activeBrackets = ArrayList<IBracketSimulator>()

            while (this.brackets.isNotEmpty() || activeBrackets.isNotEmpty()) {
                val fights = ArrayList<FightDescription>()
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
                fights.forEach { f -> this.acceptFight(f, getFightDuration(f), false) }
            }
            this.fightsByMats.forEach { m ->
                if (m.pending.isNotEmpty()) {
                    val pendingFights = Array(m.pending.size) { index -> m.pending[index] }
                    m.pending.clear()
                    pendingFights.forEach { fight ->
                        this.acceptFight(fight, getFightDuration(fight), true)
                    }
                }
            }
        }
    }

    fun generateSchedule(properties: ScheduleProperties, stages: List<StageDescriptor>, timeZone: String): Schedule {


        if (!properties.periodPropertiesList.isNullOrEmpty()) {
            val fightsByIds: Map<String, List<FightDescription>> = stages.flatMap {
                it.fights?.toList() ?: emptyList()
            }.groupBy { it.categoryId }
            if (fightsByIds.isEmpty()) {
                throw ServiceException("No fights generated.")
            }
            return doGenerateSchedule(stages, properties, timeZone)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(stages: List<StageDescriptor>, properties: ScheduleProperties, timeZone: String): Schedule {
        return Schedule(
                id = properties.id,
                periods = properties.periodPropertiesList?.mapNotNull {
                    it?.let { p ->
                        val id = createPeriodId(properties.id)
                        val periodStartTime = p.startTime
                        val brackets = p.categories.flatMap { cat -> stages.filter { stage -> stage.categoryId == cat.id } }.map { stage ->
                            bracketSimulatorFactory.createSimulator(stage.id!!, stage.categoryId, stage.fights?.toList() ?: emptyList(),
                                    stage.bracketType)
                        }
                        val composer = ScheduleComposer(periodStartTime, p.numberOfMats, brackets, BigDecimal(p.timeBetweenFights), p.riskPercent, id, timeZone)
                        composer.simulate()
                        Period(id = id,
                                schedule = composer.schedule,
                                fightsByMats = composer.fightsByMats,
                                startTime = periodStartTime,
                                name = p.name,
                                numberOfMats = p.numberOfMats,
                                categories = p.categories)
                    }
                }?.toMutableList(),
                scheduleProperties = properties)
    }
}