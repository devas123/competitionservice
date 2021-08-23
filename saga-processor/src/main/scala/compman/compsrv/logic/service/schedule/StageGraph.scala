package compman.compsrv.logic.service.schedule

import com.google.common.collect.{BiMap, HashBiMap}
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.extension._

import scala.collection.mutable

class StageGraph(stages: List[StageDescriptorDTO], fights: List[FightDescriptionDTO]) {

  private val stagesGraph: Array[List[Int]]
  private val fightsGraph: Array[List[Int]]
  private val stageIdsToIds: BiMap[String, Int] = HashBiMap.create()
  private val fightIdsToIds: BiMap[String, Int] = HashBiMap.create()
  private val fightIdToCategoryId: mutable.Map[String, String] = mutable.Map.empty
  private val fightIdToStageId: mutable.Map[String, String] = mutable.Map.empty
  private val fightIdToDuration: mutable.Map[String, BigDecimal] = mutable.Map.empty
//  private val stageIdToFightIds: Array[mutable.Set[String]]
  private val stageOrdering: Array[Int]
  private val fightsOrdering: Array[Int]
  private var nonCompleteCount: Int
  private val fightsInDegree: Array[Int]
  private val completedFights: Array[Boolean]
  private val completableFights: Set[Int]
  private val categoryIdToFightIds: Map[String, Set[String]] =
    fights.groupBy { _.getCategoryId }.view.mapValues { _.map { _.getId }.toSet }.toMap


   def this(stages: List[StageDescriptorDTO], fights: List[FightDescriptionDTO]) = {
     this(stages, fights)
    // we resolve transitive connections here (a -> b -> c ~  (a -> b, a -> c, b -> c))
    var i = 0
    for (stage <- stages) {
      if (!stageIdsToIds.containsKey(stage.getId)) {
        stageIdsToIds.put(stage.getId, i)
        i += 1
      }
    }
    val stageNodesMutable = Array.fill(i) { mutable.HashSet.empty[Int] }
    val stageIdToFightIds = Array.fill(i) { mutable.HashSet.empty[String] }

    stages.foreach { stage =>
      stage.getInputDescriptor.getSelectors.foreach { s =>
      val parentId = s.getApplyToStageId
      if (stageIdsToIds.containsKey(parentId)) {
        val nodeId = stageIdsToIds.get(stage.getId)
          stageNodesMutable(stageIdsToIds.get(parentId)).add(nodeId)
      }

    }
    }

     for {
       stageOrdering <- GraphUtils.findTopologicalOrdering(stageNodesMutable.toList.map(_.toSet))
       nonCompleteCount = i
       stagesGraph = stageNodesMutable.map { _.toList }
     } yield ()

    i = 0
    fights.sortBy { _.roundOrZero }.sortBy { it => stageOrdering(stageIdsToIds.get(it.getStageId))  }.foreach { f =>
      fightIdToCategoryId(f.getId) = f.getCategoryId
      fightIdToStageId(f.getId) = f.getStageId
      fightIdToDuration(f.getId) = f.getDuration
      if (!fightIdsToIds.containsKey(f.getId)) {
        fightIdsToIds.put(f.getId, i)
        i += 1
      }
    }

    val fightsGraphMutable = Array.fill(i) { mutable.HashSet.empty[Int] }


    fights.foreach { fight =>
      if (fight.getWinFight != null) {
        fightsGraphMutable(fightIdsToIds.get(fight.getId)).add(fightIdsToIds.get(fight.getWinFight))
      }
      if (fight.getLoseFight != null) {
        fightsGraphMutable(fightIdsToIds.get(fight.getId)).add(fightIdsToIds.get(fight.getLoseFight))
      }
      stageIdToFightIds(stageIdsToIds.get(fight.getStageId)).add(fight.getId)
    }
    val completableFights = mutable.HashSet.empty[Int]
    val fightsInDegree = GraphUtils.getIndegree(fightsGraphMutable.toList.map(_.toSet))
    fightsInDegree.zipWithIndex.foreach { e =>
    val (degree, fight) = e
      if (degree == 0) {
        completableFights.add(fight)
      }
    }
    val fightsOrdering = GraphUtils.findTopologicalOrdering(fightsGraphMutable.toList.map(_.toSet))
    val fightsGraph = fightsGraphMutable.map { _.toList }
    val completedFights = Array.fill(i) { false }
    nonCompleteCount = i
  }

  def completeFight(id: String) = {
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

  def getFightInDegree(id: String): Int {
    return fightsInDegree[fightIdsToIds[id]!!]
  }

  def flushCompletableFights(fromFights: Set<String>): List<String> {
    return fromFights.filter { completableFights.contains(fightIdsToIds[it]) && !completedFights[fightIdsToIds.getValue(it)] }
    .sortedBy { fightIdsToIds[it]?.let { f -> fightsOrdering[f] } ?: Int.MAX_VALUE }
  }

  def flushNonCompletedFights(fromFights: Set<String>): List<String> {
    return fromFights.filter { !completedFights[fightIdsToIds.getValue(it)] }
    .sortedBy { fightIdsToIds[it]?.let { f -> fightsOrdering[f] } ?: Int.MAX_VALUE }
  }

  def getCategoryId(id: String): String {
    return fightIdToCategoryId[id]!!
  }
  def getStageId(id: String): String {
    return fightIdToStageId[id]!!
  }
  def getDuration(id: String): BigDecimal {
    return fightIdToDuration[id]!!
  }

  def getStageFights(stageId: String) = {
    if (stageIdsToIds(stageId)) {
    stageIdToFightIds[stageIdsToIds[stageId]!!]
  } else {
    emptySet()
  }
  }

  def getCategoryIdsToFightIds = {
    categoryIdToFightIds
  }

  def getNonCompleteCount: Int = {
    return nonCompleteCount
  }
}