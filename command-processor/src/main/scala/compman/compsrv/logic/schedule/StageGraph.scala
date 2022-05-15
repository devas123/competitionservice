package compman.compsrv.logic.schedule

import com.google.common.collect.{BiMap, HashBiMap}
import compman.compsrv.Utils
import compman.compsrv.logic.fight.CanFail
import compman.compsrv.model.extensions._
import compservice.model.protobuf.model._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class StageGraph private[schedule](
                                         private val fightsMap: Map[String, FightDescription],
                                         private val stagesGraph: Array[List[Int]],
                                         private val fightsGraph: Array[List[Int]],
                                         private val stageIdsToIds: BiMap[String, Int] = HashBiMap.create(),
                                         private val fightIdsToIds: BiMap[String, Int] = HashBiMap.create(),
                                         private val stageOrdering: Array[Int],
                                         private val fightsOrdering: Array[Int],
                                         private val nonCompleteCount: Int,
                                         private val fightsInDegree: Array[Int],
                                         private val completedFights: Set[Int],
                                         private val completableFights: Set[Int],
                                         private val categoryIdToFightIds: Map[String, Set[String]],
                                         private val stageIdsToFightIds: Array[List[String]]
) {

  def getFightInDegree(id: String): Int = { fightsInDegree(fightIdsToIds.get(id)) }

  def flushCompletableFights(fromFights: Set[String]): List[String] = {
    fromFights.filter { it =>
      completableFights.contains(fightIdsToIds.get(it)) && !completedFights(fightIdsToIds.get(it))
    }.toList.sortedFights
  }

  private implicit class ListStringOps(l: List[String]) {
    def sortedFights: List[String] = l.sortBy { it =>
      Option(fightIdsToIds.get(it)).map { f => fightsOrdering(f) }.getOrElse(Int.MaxValue)
    }
  }

  def flushNonCompletedFights(fromFights: Set[String]): List[String] = {
    fromFights.filter { it => completedFights(fightIdsToIds.get(it)) }.toList.sortedFights
  }

  def getCategoryId(id: String): String = { fightsMap(id).categoryId }

  def stageId(id: String): String = { fightsMap(id).stageId }

  def getDuration(id: String): Int = { fightsMap(id).duration }

  def getStageFights(stageId: String): List[String] = {
    if (stageIdsToIds.containsKey(stageId)) { stageIdsToFightIds(stageIdsToIds.get(stageId)) }
    else { List.empty[String] }
  }

  def getCategoryIdsToFightIds: Map[String, Set[String]] = { categoryIdToFightIds }

  def getNonCompleteCount: Int = { nonCompleteCount }
}

object StageGraph {

  import scala.jdk.CollectionConverters._

  def completeFight(id: String, stageGraph: StageGraph): StageGraph = {
    val i = stageGraph.fightIdsToIds.get(id)
    if (!stageGraph.completedFights(i)) {
      val newVertices       = ArrayBuffer.empty[Int]
      val newFightsInDegree = Array.copyOf(stageGraph.fightsInDegree, stageGraph.fightsInDegree.length)
      stageGraph.fightsGraph(i).foreach { adj =>
        if (!stageGraph.completedFights(adj)) {
          newFightsInDegree(adj) -= 1
          if (newFightsInDegree(adj) <= 0) { newVertices.append(adj) }
        }
      }
      stageGraph.copy(
        completedFights = stageGraph.completedFights + i,
        nonCompleteCount = stageGraph.nonCompleteCount - 1,
        completableFights = (stageGraph.completableFights ++ newVertices) - i,
        fightsInDegree = newFightsInDegree
      )
    } else { stageGraph }
  }

  private def createStagesGraph(
    stages: List[StageDescriptor],
    stageIdsToIds: BiMap[String, Int]
  ): Array[List[Int]] = {
    val stageNodesMutable = Array.fill(stageIdsToIds.keySet().size()) { mutable.HashSet.empty[Int] }
    stages.foreach { stage =>
      stage.inputDescriptor.map(_.selectors).foreach(arr => arr.foreach { s =>
        val parentId = s.applyToStageId
        if (stageIdsToIds.containsKey(parentId)) {
          val nodeId = stageIdsToIds.get(stage.id)
          stageNodesMutable(stageIdsToIds.get(parentId)).add(nodeId)
        }
      })
    }
    stageNodesMutable.map { _.toList }
  }

  private def createStageIdsToIntIds(stages: List[StageDescriptor]) = {
    val indices = stages.distinctBy(_.id).zipWithIndex.map(p => (p._1.id, p._2)).toMap
    HashBiMap.create(indices.asJava)
  }
  private def createFightIdsToIntIds(fights: List[FightDescription]) = {
    HashBiMap.create(fights.distinctBy(_.id).zipWithIndex.map(p => (p._1.id, p._2)).toMap.asJava)
  }

  private def getFightsGraphAndStageIdToFightId(
    fights: List[FightDescription],
    stagesNumber: Int,
    fightsNumber: Int,
    fightIdsToIds: BiMap[String, Int],
    stageIdsToIds: BiMap[String, Int]
  ) = {
    val fightsGraphMutable = Array.fill(fightsNumber) { mutable.HashSet.empty[Int] }
    val stageIdToFightIds  = Array.fill(stagesNumber) { mutable.HashSet.empty[String] }

    fights.foreach { fight =>
      if (fight.winFight.isDefined) {
        fightsGraphMutable(fightIdsToIds.get(fight.id)).add(fightIdsToIds.get(fight.getWinFight))
      }
      if (fight.loseFight.isDefined) {
        fightsGraphMutable(fightIdsToIds.get(fight.id)).add(fightIdsToIds.get(fight.getLoseFight))
      }
      stageIdToFightIds(stageIdsToIds.get(fight.stageId)).add(fight.id)
    }
    (fightsGraphMutable.map(_.toList), stageIdToFightIds.map(_.toList))
  }

  private def getCompletableFights(fightsInDegree: Array[Int]) = {
    val completableFights = mutable.HashSet.empty[Int]

    fightsInDegree.zipWithIndex.foreach { e =>
      val (degree, fight) = e
      if (degree == 0) { completableFights.add(fight) }
    }
    completableFights.toSet
  }

  def create(stages: List[StageDescriptor], fights: List[FightDescription]): CanFail[StageGraph] = {
    // we resolve transitive connections here (a -> b -> c  ~>  (a -> b, a -> c, b -> c))
    val stageIdsToIds = createStageIdsToIntIds(stages)
    val stagesGraph   = createStagesGraph(stages, stageIdsToIds)

    for {
      stageOrdering <- GraphUtils.findTopologicalOrdering(stagesGraph)
      sortedFights  = fights.sortBy { _.roundOrZero }.sortBy { it => stageOrdering(stageIdsToIds.get(it.stageId)) }
      fightsMap     = Utils.groupById(sortedFights)(_.id)
      fightIdsToIds = createFightIdsToIntIds(sortedFights)
      (fightsGraph, stageIdsToFightIds) =
        getFightsGraphAndStageIdToFightId(fights, stages.size, fightsMap.size, fightIdsToIds, stageIdsToIds)
      fightsInDegree = GraphUtils.getIndegree(fightsGraph)
      fightsOrdering <- GraphUtils.findTopologicalOrdering(fightsGraph)
      completableFights    = getCompletableFights(fightsInDegree)
      completedFights      = Set.empty[Int]
      categoryIdToFightIds = fights.groupMap(_.categoryId)(_.id).map { case (k, list) => k -> list.toSet }
    } yield new StageGraph(
      stagesGraph = stagesGraph,
      fightsGraph = fightsGraph,
      stageIdsToIds = stageIdsToIds,
      fightIdsToIds = fightIdsToIds,
      fightsMap = fightsMap,
      stageOrdering = stageOrdering,
      fightsOrdering = fightsOrdering,
      nonCompleteCount = fightsMap.size,
      fightsInDegree = fightsInDegree,
      completedFights = completedFights,
      completableFights = completableFights,
      categoryIdToFightIds = categoryIdToFightIds,
      stageIdsToFightIds = stageIdsToFightIds
    )
  }

}
