package compman.compsrv.service

import com.compmanager.service.ServiceException
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.competition.FightDescription
import compman.compsrv.model.schedule.*
import compman.compsrv.model.schedule.Schedule.Companion.obsoleteFight
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

@Component
class ScheduleService {

    companion object {
        fun createPeriodId(competitionId: String) = "$competitionId-period-${UUID.randomUUID()}"
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
                        .forEach { _, u ->
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

    private class ScheduleComposer(startTime: Date, val numberOfMats: Int, val fightDurations: Map<String, BigDecimal>, brackets: List<BracketSimulator>, val pause: BigDecimal, riskFactor: BigDecimal, periodId: String) {
        val schedule: MutableList<ScheduleEntry>
        val fightsByMats: ArrayList<MatScheduleContainer> = ArrayList(numberOfMats)
        val riskCoeff: BigDecimal
        val brackets: ArrayList<BracketSimulator> = ArrayList(brackets)

        init {
            this.schedule = ArrayList()
            for (i in 0 until numberOfMats) {
                val initDate = Date(startTime.time)
                fightsByMats.add(MatScheduleContainer(initDate, "$periodId-mat-$i"))
            }
            riskCoeff = BigDecimal.ONE.plus(riskFactor)
        }

        fun categoryNotRegistered(categoryId: String): Boolean {
            return this.schedule.size == 0 || !this.schedule.map { e -> e.categoryId }.contains(categoryId)
        }

        fun updateSchedule(f: FightDescription, startTime: Date) {
            if (this.categoryNotRegistered(f.categoryId)) {
                this.schedule.add(ScheduleEntry(
                        categoryId = f.categoryId,
                        startTime = DateTimeFormatter.ISO_INSTANT.format(startTime.toInstant()),
                        numberOfFights = 0,
                        fightDuration = BigDecimal.valueOf(f.duration ?: 0L)))
            }
        }

        fun acceptFight(f: FightDescription, duration: BigDecimal, lastrun: Boolean?) {
            val freshMat = this.fightsByMats.find { it.fights.isEmpty() }
            if (freshMat != null) {
                val currentTime = Date(freshMat.currentTime.time)
                this.updateSchedule(f, currentTime)
                freshMat.currentTime.time = currentTime.time + duration.toLong() * 60L * 1000L
                freshMat.fights += FightStartTimePair(f, freshMat.currentFightNumber++, currentTime)
            } else {
                if (this.categoryNotRegistered(f.categoryId)) {
                    val mat = this.fightsByMats.sortedBy { a -> a.currentTime.time }.first()
                    val currentTime = Date(mat.currentTime.time)
                    this.updateSchedule(f, currentTime)
                    mat.currentTime.time = currentTime.time + duration.toLong() * 60 * 1000
                    mat.fights += FightStartTimePair(f, mat.currentFightNumber++, currentTime)
                } else {
                    val mat: Any
                    val matsWithTheSameCategory = this.fightsByMats
                            .filter { m ->
                                m.fights.isNotEmpty() && m.fights.last().fight.categoryId == f.categoryId && (m.fights.last().fight.round
                                        ?: -1) < (f.round ?: -1)
                            }
                    if (matsWithTheSameCategory.isNotEmpty() && lastrun != true) {
                        mat = matsWithTheSameCategory.sortedBy { it.currentTime.time }.first()
                        mat.pending.add(f)
                    } else {
                        mat = this.fightsByMats.sortedBy { it.currentTime.time }.first()
                        val currentTime = Date(mat.currentTime.time)
                        this.updateSchedule(f, currentTime)
                        mat.currentTime.time = currentTime.time + duration.toLong() * 60 * 1000
                        mat.fights += FightStartTimePair(f, mat.currentFightNumber++, currentTime)
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


    fun generateSchedule(properties: ScheduleProperties, brackets: List<BracketDescriptor>, fightDurations: Map<String, BigDecimal>): Schedule {
        fun getNumberOfFights(categoryId: String): Int {
            val categoryBrackets = brackets
                    .filter { it.fights.any { f -> f.categoryId == categoryId } }
            val categoryCompetitors = categoryBrackets.flatMap { it.fights.toList() }.flatMap { fight -> fight.competitors.map { it.competitor } }.toSet()
            return brackets
                    .filter { it.fights.any { f -> f.categoryId == categoryId } }
                    .map { bracketDescriptor -> bracketDescriptor.fights.filter { !Schedule.obsoleteFight(it, categoryCompetitors.size == 3) }.size }
                    .fold(0) { acc, i -> acc + i }
        }


        if (properties.periodPropertiesList.isNotEmpty()) {
            val fightsByIds: Map<String, List<FightDescription>> = brackets.flatMap { it.fights.toList() }.groupBy { it.categoryId }
            if (fightsByIds.isEmpty()) {
                throw ServiceException("No fights generated.")
            }
            val exceptionCategoryIds: List<String> = fightsByIds.filter { (it.value.size == 3 && it.value.any { fd -> !fd.loseFight.isNullOrBlank() }) || it.key.endsWith("ABSOLUTE") }.keys.toList()
            return doGenerateSchedule(fightsByIds, exceptionCategoryIds, properties, fightDurations)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(fightsByIds: Map<String, List<FightDescription>>, exceptionCategoryIds: List<String>, properties: ScheduleProperties, fightDurations: Map<String, BigDecimal>): Schedule {
        return Schedule(
                id = properties.competitionId,
                periods = properties.periodPropertiesList.map { p ->
                    val id = createPeriodId(properties.competitionId)
                    val periodStartTime = Date(p.startTime.time)
                    val brackets = p.categories.map { cat -> BracketSimulator(fightsByIds[cat.id], exceptionCategoryIds.contains(cat.id)) }
                    val composer = ScheduleComposer(periodStartTime, p.numberOfMats, fightDurations, brackets, BigDecimal(p.timeBetweenFights), p.riskPercent, id)
                    composer.simulate()
                    Period(id = id,
                            schedule = composer.schedule,
                            fightsByMats = composer.fightsByMats,
                            startTime = DateTimeFormatter.ISO_INSTANT.format(periodStartTime.toInstant()),
                            name = p.id,
                            numberOfMats = p.numberOfMats,
                            categories = p.categories)
                },
                scheduleProperties = properties)
    }
}