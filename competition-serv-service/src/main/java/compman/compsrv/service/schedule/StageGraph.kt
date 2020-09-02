package compman.compsrv.service.schedule

import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class StageGraph(stages: List<StageDescriptorDTO>, fights: List<FightDescription>) {

    companion object {
        private val log = LoggerFactory.getLogger(StageGraph::class.java)
    }

    private val stagesGraph: Array<List<Int>>
    private val fightsGraph: Array<List<Int>>
    private val stageIdsToIds: BiMap<String, Int>
    private val fightIdsToIds: BiMap<String, Int>
    private val fightIdToCategoryId: MutableMap<String, String> = mutableMapOf()
    private val fightIdToStageId: MutableMap<String, String> = mutableMapOf()
    private val fightIdToDuration: MutableMap<String, BigDecimal> = mutableMapOf()
    private val stageIdToFightIds: Array<MutableSet<String>>
    private val stageOrdering: IntArray
    private val fightsOrdering: IntArray
    private var nonCompleteCount: Int
    private val fightsInDegree: IntArray
    private val completedFights: BooleanArray
    private val completableFights: MutableSet<Int>
    private val categoryIdToFightIds: Map<String, Set<String>>



    init {

        categoryIdToFightIds = fights.groupBy { it.categoryId }.mapValues { e -> e.value.map { it.id }.toSet() }
        stageIdsToIds = HashBiMap.create()
        fightIdsToIds = HashBiMap.create()
        // we resolve transitive connections here (a -> b -> c ~  (a -> b, a -> c, b -> c))
        var i = 0
        for (stage in stages) {
            if (!stageIdsToIds.containsKey(stage.id)) {
                stageIdsToIds[stage.id] = i++
            }
        }
        val stageNodesMutable = Array<MutableSet<Int>>(i) { HashSet() }
        stageIdToFightIds = Array<MutableSet<String>>(i) { HashSet() }

        stages.forEach { stage ->
            stage.inputDescriptor?.selectors?.forEach { s ->
                s.applyToStageId?.let { parentId ->
                    if (stageIdsToIds.containsKey(parentId)) {
                        val nodeId = stageIdsToIds[stage.id]!!
                        stageNodesMutable[stageIdsToIds[parentId]!!].add(nodeId)
                    }
                }
            }
        }

        stageOrdering = GraphUtils.findTopologicalOrdering(stageNodesMutable)
        nonCompleteCount = i
        stagesGraph = stageNodesMutable.map { it.toList() }.toTypedArray()

        i = 0
        fights.sortedBy { it.round }.sortedBy { stageOrdering[stageIdsToIds[it.stageId]!!] }.forEach { f ->
            fightIdToCategoryId[f.id] = f.categoryId
            fightIdToStageId[f.id] = f.stageId
            fightIdToDuration[f.id] = f.duration
            if (!fightIdsToIds.containsKey(f.id)) {
                fightIdsToIds[f.id] = i++
            }
        }

        val fightsGraphMutable = Array<MutableSet<Int>>(i) { HashSet() }


        fights.forEach { fight ->
            if (!fight.winFight.isNullOrBlank()) {
                fightsGraphMutable[fightIdsToIds[fight.id]!!].add(fightIdsToIds[fight.winFight]!!)
            }
            if (!fight.loseFight.isNullOrBlank()) {
                fightsGraphMutable[fightIdsToIds[fight.id]!!].add(fightIdsToIds[fight.loseFight]!!)
            }
            stageIdToFightIds[stageIdsToIds[fight.stageId]!!].add(fight.id)
        }
        completableFights = HashSet()
        fightsInDegree = GraphUtils.getIndegree(fightsGraphMutable)
        fightsInDegree.forEachIndexed { fight, degree ->
            if (degree == 0) {
                completableFights.add(fight)
            }
        }
        fightsOrdering = GraphUtils.findTopologicalOrdering(fightsGraphMutable)
        fightsGraph = fightsGraphMutable.map { it.toList() }.toTypedArray()
        completedFights = BooleanArray(i) { false }
        nonCompleteCount = i
    }

    fun completeFight(id: String) {
        val i = fightIdsToIds[id]
        if (i != null && !completedFights[i]) {
            completedFights[i] = true
            fightsGraph[i].forEach { adj ->
                if (!completedFights[adj]) {
                    if (--fightsInDegree[adj] <= 0) {
                        completableFights.add(adj)
                    }
                }
            }
            nonCompleteCount--
            completableFights.remove(i)
        }
    }

    fun getFightInDegree(id: String): Int {
        return fightsInDegree[fightIdsToIds[id]!!]
    }

    fun flushCompletableFights(fromFights: Set<String>): List<String> {
        return fromFights.filter { completableFights.contains(fightIdsToIds[it]) && !completedFights[fightIdsToIds.getValue(it)] }
                .sortedBy { fightIdsToIds[it]?.let { f -> fightsOrdering[f] } ?: Int.MAX_VALUE }
    }

    fun flushNonCompletedFights(fromFights: Set<String>): List<String> {
        return fromFights.filter { !completedFights[fightIdsToIds.getValue(it)] }
                .sortedBy { fightIdsToIds[it]?.let { f -> fightsOrdering[f] } ?: Int.MAX_VALUE }
    }

    fun getCategoryId(id: String): String {
        return fightIdToCategoryId[id]!!
    }
    fun getStageId(id: String): String {
        return fightIdToStageId[id]!!
    }
    fun getDuration(id: String): BigDecimal {
        return fightIdToDuration[id]!!
    }

    fun getStageFights(stageId: String): Set<String> {
        return if (stageIdsToIds.containsKey(stageId)) {
            stageIdToFightIds[stageIdsToIds[stageId]!!]
        } else {
            emptySet()
        }
    }

    fun getCategoryIdsToFightIds(): Map<String, Set<String>> {
        return categoryIdToFightIds
    }

    fun getNonCompleteCount(): Int {
        return nonCompleteCount
    }

}