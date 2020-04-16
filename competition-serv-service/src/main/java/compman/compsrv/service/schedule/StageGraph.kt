package compman.compsrv.service.schedule

import com.compmanager.compservice.jooq.tables.pojos.CompScore
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

data class StageNode(val stage: StageDescriptorDTO, val parentStageIds: Set<String>, val fights: List<FightDescription>, val brackets: IBracketSimulator)

class StageGraph(override val categoryId: String, private val stages: List<StageDescriptorDTO>, private val fights: List<FightDescription>,
                 private val bracketSimulatorFactory: BracketSimulatorFactory, getFightScores: (id: String) -> List<CompScore>) : IBracketSimulator {

    companion object {
        private val log = LoggerFactory.getLogger(StageGraph::class.java)
    }

    private val stageNodes: List<StageNode>

    private val completeStages = mutableListOf<String>()

    private val empty = AtomicBoolean(false)

    init {
        // we resolve transitive connections here (a -> b -> c ~  (a -> b, a -> c, b -> c))
        stageNodes = stages.fold(emptyList<StageNode>()) { acc, stage ->
            val parentIds = getParentIds(stage)
            val stageFights = fights.filter { f -> f.stageId == stage.id }
            acc + StageNode(stage, parentIds.toSet(), stageFights,
                    bracketSimulatorFactory.createSimulator(stage.id, categoryId, stageFights, stage.bracketType, stage.inputDescriptor.numberOfCompetitors, getFightScores))
        }.sortedBy { it.stage.stageOrder }
    }

    private fun getParentIds(stage: StageDescriptorDTO): Set<String> {
        tailrec fun loop(result: Set<String>,  parents: List<StageDescriptorDTO>): Set<String> {
            log.info("Loop: $result, $parents")
            if (parents.all { it.stageOrder <= 0 }) {
                log.info("Return: $result, ${parents.map { it.id }.toSet()}")
                return result + parents.map { it.id }.toSet()
            }
            val directParents = parents.fold(emptyList<StageDescriptorDTO>()) { acc, stg ->
                acc + stages.filter { st -> st.stageOrder < stg.stageOrder } +
                        stg.inputDescriptor.selectors?.mapNotNull { it?.applyToStageId }?.map { stages.first { st -> st.id == it } }.orEmpty()
            }
            return loop(result + parents.map { it.id }.toSet(), directParents)
        }
        return loop(emptySet(), stages.filter { st -> st.stageOrder < stage.stageOrder } +
                stage.inputDescriptor.selectors?.mapNotNull { it?.applyToStageId }?.map { stages.first { st -> st.id == it } }.orEmpty())
    }

    override fun isEmpty(): Boolean {
        return empty.get() || stageNodes.all { it.brackets.isEmpty() }
    }

    override fun getNextRound(): List<FightDescription> {
        val node = stageNodes.firstOrNull { !completeStages.contains(it.stage.id) && completeStages.containsAll(it.parentStageIds) && !it.brackets.isEmpty() }
        val nextRound = node?.brackets?.getNextRound().orEmpty()
        if (node != null && node.brackets.isEmpty()) {
            completeStages.add(node.stage.id)
        }
        if (node == null) {
            empty.lazySet(true)
        }
        return nextRound
    }

    override val stageIds = stages.map { it.id }.toSet()
}