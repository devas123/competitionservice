package compman.compsrv.service

import com.compmanager.service.ServiceException
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.jpa.schedule.*
import compman.compsrv.jpa.schedule.Schedule.Companion.obsoleteFight
import compman.compsrv.repository.FightCrudRepository
import compman.compsrv.util.IDGenerator
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.ArrayList

@Component
class ScheduleService(private val fightCrudRepository: FightCrudRepository) {

    companion object {
        fun createPeriodId(competitionId: String) = IDGenerator.hashString("$competitionId-period-${UUID.randomUUID()}")
        fun createMatId(periodId: String, matNumber: Int) = IDGenerator.hashString("$periodId-mat-$matNumber")
    }

    private class BracketSimulator(fights: List<FightDescription>?, threeCompetitorCategory: Boolean) {
        val fightsByRounds: MutableList<List<FightDescription>?>

        init {
            fightsByRounds = ArrayList()
            if (fights?.isNotEmpty() == true) {
                fights
                        .filter { it.round != null && !obsoleteFight(it, threeCompetitorCategory) }
                        .groupBy { it.round ?: 0 }
                        .toSortedMap(kotlin.Comparator { a, b -> (a ?: 0) - (b ?: 0) })
                        .forEach { (_, u) ->
                            fightsByRounds.add(u ?: ArrayList())
                        }
            }
        }

        fun isEmpty() = this.fightsByRounds.isEmpty()

        fun getNextRound(): List<FightDescription> {
            return if (this.fightsByRounds.size > 0) {
                this.fightsByRounds.removeAt(0) ?: ArrayList()
            } else {
                ArrayList()
            }
        }
    }

    private class ScheduleComposer(startTime: Instant, val numberOfMats: Int, val fightDurations: Map<String, BigDecimal>, brackets: List<BracketSimulator>, val pause: BigDecimal, riskFactor: BigDecimal, periodId: String, val timeZone: String) {
        val schedule: MutableList<ScheduleEntry>
        val fightsByMats: ArrayList<MatScheduleContainer> = ArrayList(numberOfMats)
        val riskCoeff: BigDecimal
        val brackets: ArrayList<BracketSimulator> = ArrayList(brackets)

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
                        fightDuration = BigDecimal.valueOf(f.duration ?: 0L)))
            } else {
                val entry = this.schedule.first { it.categoryId == f.categoryId }
                entry.numberOfFights += 1
            }
        }

        fun acceptFight(f: FightDescription, duration: BigDecimal, lastrun: Boolean?) {
            val freshMat = this.fightsByMats.find { it.fights.isEmpty() }
            if (freshMat != null) {
                val currentTime = ZonedDateTime.ofInstant(freshMat.currentTime, ZoneId.of(timeZone))
                this.updateSchedule(f, currentTime.toInstant())
                freshMat.currentTime = currentTime.plusSeconds(duration.toLong() * 60L).toInstant()
                freshMat.fights += FightStartTimePair(f, freshMat.totalFights++, currentTime.toInstant())
            } else {
                if (this.categoryNotRegistered(f.categoryId)) {
                    val mat = this.fightsByMats.sortedBy { a -> a.currentTime.toEpochMilli() }.first()
                    val currentTime = mat.currentTime
                    this.updateSchedule(f, currentTime)
                    mat.currentTime = currentTime.plusSeconds(duration.toLong() * 60)
                    mat.fights += FightStartTimePair(f, mat.totalFights++, currentTime)
                } else {
                    val mat: MatScheduleContainer
                    val matsWithTheSameCategory = this.fightsByMats
                            .filter { m ->
                                m.fights.isNotEmpty() && m.fights.last().fight.categoryId == f.categoryId && (m.fights.last().fight.round
                                        ?: -1) < (f.round ?: -1)
                            }
                    if (matsWithTheSameCategory.isNotEmpty() && lastrun != true) {
                        mat = matsWithTheSameCategory.sortedBy { it.currentTime.toEpochMilli() }.first()
                        mat.pending.add(f)
                    } else {
                        mat = this.fightsByMats.sortedBy { it.currentTime.toEpochMilli() }.first()
                        val currentTime = mat.currentTime
                        this.updateSchedule(f, currentTime)
                        mat.currentTime = currentTime.plusSeconds(duration.toLong() * 60)
                        mat.fights += FightStartTimePair(f, mat.totalFights++, currentTime)
                        this.fightsByMats.forEach { m ->
                            if (m.pending.isNotEmpty()) {
                                val pendingFights = Array(m.pending.size) { index -> m.pending[index] }
                                m.pending.clear()
                                pendingFights.forEach { fight ->
                                    this.acceptFight(fight, getFightDuration(fight.categoryId), lastrun)
                                }
                            }
                        }
                    }
                }
            }

        }

        fun getFightDuration(catId: String) = (fightDurations[catId]
                ?: BigDecimal(7)).multiply(riskCoeff).plus(this.pause)

        fun simulate() {
            val activeBrackets = ArrayList<BracketSimulator>()

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
                fights.forEach { f -> this.acceptFight(f, getFightDuration(f.categoryId), false) }
            }
            this.fightsByMats.forEach { m ->
                if (m.pending.isNotEmpty()) {
                    val pendingFights = Array(m.pending.size) { index -> m.pending[index] }
                    m.pending.clear()
                    pendingFights.forEach { fight ->
                        this.acceptFight(fight, getFightDuration(fight.categoryId), true)
                    }
                }
            }
        }
    }

    private fun getNumberOfFights(categoryId: String): Int {
        return fightCrudRepository.countByCategoryId(categoryId)
    }


    fun generateSchedule(properties: ScheduleProperties, brackets: List<BracketDescriptor>, fightDurations: Map<String, BigDecimal>, timeZone: String): Schedule {


        if (properties.periodPropertiesList.isNotEmpty()) {
            val fightsByIds: Map<String, List<FightDescription>> = brackets.flatMap { it.fights.toList() }.groupBy { it.categoryId }
            if (fightsByIds.isEmpty()) {
                throw ServiceException("No fights generated.")
            }
            val exceptionCategoryIds: List<String> = fightsByIds.filter { (it.value.size == 3 && it.value.any { fd -> !fd.loseFight.isNullOrBlank() }) || it.key.endsWith("ABSOLUTE") }.keys.toList()
            return doGenerateSchedule(fightsByIds, exceptionCategoryIds, properties, fightDurations, timeZone)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(fightsByIds: Map<String, List<FightDescription>>, exceptionCategoryIds: List<String>, properties: ScheduleProperties, fightDurations: Map<String, BigDecimal>, timeZone: String): Schedule {
        return Schedule(
                id = properties.id,
                periods = properties.periodPropertiesList.map { p ->
                    val id = createPeriodId(properties.id)
                    val periodStartTime = p.startTime
                    val brackets = p.categories.map { cat -> BracketSimulator(fightsByIds[cat.id], exceptionCategoryIds.contains(cat.id)) }
                    val composer = ScheduleComposer(periodStartTime, p.numberOfMats, fightDurations, brackets, BigDecimal(p.timeBetweenFights), p.riskPercent, id, timeZone)
                    composer.simulate()
                    Period(id = id,
                            schedule = composer.schedule,
                            fightsByMats = composer.fightsByMats,
                            startTime = periodStartTime,
                            name = p.name,
                            numberOfMats = p.numberOfMats,
                            categories = p.categories)
                },
                scheduleProperties = properties)
    }
}