package compman.compsrv.service.schedule

import arrow.core.extensions.list.align.empty
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import compman.compsrv.model.dto.schedule.ScheduleRequirementDTO
import compman.compsrv.model.dto.schedule.ScheduleRequirementType

class RequirementsGraph(requirements: Map<String, ScheduleRequirementDTO>, categoryIdToFightIds: Map<String, Set<String>>, periods: Array<String>) {
    val requirementIdToId: BiMap<String, Int> = HashBiMap.create()
    private val graph: Array<List<Int>>
    private val categoryRequirements: MutableMap<String, MutableList<Int>>
    private val requirementFightIds: Array<MutableSet<String>>
    val orderedRequirements: List<ScheduleRequirementDTO>
    private val requirementFightsSize: IntArray
    val size: Int

    init {
        val fightIdToCategoryId = mutableMapOf<String, String>()
        categoryIdToFightIds.forEach { (t, u) ->
            u.forEach { fid -> fightIdToCategoryId[fid] = t }
        }
        var n = 0
        val sorted = requirements.values.sortedBy { it.entryOrder }.sortedBy { periods.indexOf(it.periodId) }
        sorted.forEach { req ->
            if (!requirementIdToId.containsKey(req.id)) {
                requirementIdToId[req.id] = n++
            }
        }
        categoryRequirements = mutableMapOf()
        val requirementsGraph = Array<MutableSet<Int>>(n) { HashSet() }
        requirementFightIds = Array(n) { HashSet<String>() }
        for (i in sorted.indices) {
            val r = sorted[i]
            val id = requirementIdToId[r.id]!!
            val categories = r.categoryIds?.toList().orEmpty() + r.fightIds?.mapNotNull { fightIdToCategoryId[it] }?.toSet().orEmpty()
            categories.forEach {
                categoryRequirements.computeIfAbsent(it) { mutableListOf() }.add(id)
            }
            if (i > 0) {
                requirementsGraph[requirementIdToId[sorted[i - 1].id]!!].add(id)
            }
        }

        categoryRequirements.forEach { e ->
            val dispatchedFights = e.value.map { ri -> requirements.getValue(requirementIdToId.inverse()[ri]!!) }.filter { it.entryType == ScheduleRequirementType.FIGHTS }
                    .flatMap { it.fightIds?.toList().orEmpty() }.toSet()
            e.value.forEach { ri ->
                val r = requirements.getValue(requirementIdToId.inverse()[ri]!!)
                if (r.entryType == ScheduleRequirementType.FIGHTS) {
                    requirementFightIds[ri].addAll(r.fightIds ?: emptyArray())
                } else if (r.entryType == ScheduleRequirementType.CATEGORIES) {
                    requirementFightIds[ri].addAll(categoryIdToFightIds[e.key].orEmpty() - dispatchedFights)
                }
            }
        }

        requirementFightsSize = IntArray(n) { ind -> requirementFightIds[ind].size }


        size = n
        val ordering = GraphUtils.findTopologicalOrdering(requirementsGraph, false)
        graph = requirementsGraph.map { it.toList() }.toTypedArray()
        orderedRequirements = sorted.sortedBy { ordering[requirementIdToId[it.id]!!] }
    }

    fun getRequirementsFightsSize(): IntArray {
        return requirementFightsSize.copyOf()
    }

    fun getIndex(id: String): Int {
        return requirementIdToId[id] ?: error("Requirement's $id index not found")
    }

    fun getRequirementsForCategory(categoryId: String): List<Int> {
        return categoryRequirements[categoryId] ?: empty()
    }

    fun getFightIdsForRequirement(reqId: String): Set<String> {
        return if (requirementIdToId.containsKey(reqId)) {
            requirementFightIds[requirementIdToId.getValue(reqId)]
        } else {
            emptySet()
        }
    }
}