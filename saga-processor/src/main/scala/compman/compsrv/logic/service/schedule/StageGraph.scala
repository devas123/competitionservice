package compman.compsrv.logic.service.schedule

import com.google.common.collect.{BiMap, HashBiMap}
import compman.compsrv.logic.service.generate.CanFail
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.extension._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.language.reflectiveCalls

case class StageGraph private[schedule] (
                       private val fightsMap: Map[String, FightDescriptionDTO],
                       private val stagesGraph: Array[List[Int]],
                       private val fightsGraph: Array[List[Int]],
                       private val stageIdsToIds: BiMap[String, Int] = HashBiMap.create(),
                       private val fightIdsToIds: BiMap[String, Int] = HashBiMap.create(),
                       //  private val stageIdToFightIds: Array[mutable.Set[String]],
                       private val stageOrdering: Array[Int],
                       private val fightsOrdering: Array[Int],
                       private val nonCompleteCount: Int,
                       private val fightsInDegree: Array[Int],
                       private val completedFights: Set[Int],
                       private val completableFights: Set[Int],
                       private val categoryIdToFightIds: Map[String, List[String]],
                       private val stageIdsToFightIds: Array[List[String]]
                     ) {

  def getFightInDegree(id: String): Int = {
    fightsInDegree(fightIdsToIds.get(id))
  }

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

  def getCategoryId(id: String): String = {
    fightsMap(id).getCategoryId
  }

  def getStageId(id: String): String = {
    fightsMap(id).getStageId
  }

  def getDuration(id: String): BigDecimal = {
    fightsMap(id).getDuration
  }

  def getStageFights(stageId: String): List[String] = {
    if (stageIdsToIds.containsKey(stageId)) {
      stageIdsToFightIds(stageIdsToIds.get(stageId))
    }
    else {
      List.empty[String]
    }
  }

  def getCategoryIdsToFightIds: Map[String, List[String]] = {
    categoryIdToFightIds
  }

  def getNonCompleteCount: Int = {
    nonCompleteCount
  }
}

object StageGraph {

  import scala.jdk.CollectionConverters._

  def completeFight(id: String, stageGraph: StageGraph): StageGraph = {
    val i = stageGraph.fightIdsToIds.get(id)
    if (!stageGraph.completedFights(i)) {
      val newVertices = ArrayBuffer.empty[Int]
      stageGraph.fightsGraph(i).foreach { adj =>
        if (!stageGraph.completedFights(adj)) {
          stageGraph.fightsInDegree(adj) -= 1
          if (stageGraph.fightsInDegree(adj) <= 0) {
            newVertices.append(adj)
          }
        }
      }
      stageGraph.copy(completedFights = stageGraph.completedFights + i, nonCompleteCount = stageGraph.nonCompleteCount - 1, completableFights = (stageGraph.completableFights ++ newVertices) - i)
    }
  }

  private def createStagesGraph(
                                 stages: List[StageDescriptorDTO],
                                 stageIdsToIds: BiMap[String, Int]
                               ): Array[List[Int]] = {
    val stageNodesMutable = Array.fill(stageIdsToIds.keySet().size()) {
      mutable.HashSet.empty[Int]
    }
    stages.foreach { stage =>
      stage.getInputDescriptor.getSelectors.foreach { s =>
        val parentId = s.getApplyToStageId
        if (stageIdsToIds.containsKey(parentId)) {
          val nodeId = stageIdsToIds.get(stage.getId)
          stageNodesMutable(stageIdsToIds.get(parentId)).add(nodeId)
        }
      }
    }
    stageNodesMutable.map {
      _.toList
    }
  }

  private def createEntityIdsToIntIds[T <: {def getId: String}](stages: List[T]) = {
    val indices = stages.distinctBy(_.getId).zipWithIndex.map(p => (p._1.getId, p._2)).toMap
    HashBiMap.create(indices.asJava)
  }

  private def getFightsGraphAndStageIdToFightId(
                                                 fights: List[FightDescriptionDTO],
                                                 stagesNumber: Int,
                                                 fightsNumber: Int,
                                                 fightIdsToIds: BiMap[String, Int],
                                                 stageIdsToIds: BiMap[String, Int]
                                               ) = {
    val fightsGraphMutable = Array.fill(fightsNumber) {
      mutable.HashSet.empty[Int]
    }
    val stageIdToFightIds = Array.fill(stagesNumber) {
      mutable.HashSet.empty[String]
    }

    fights.foreach { fight =>
      if (fight.getWinFight != null) {
        fightsGraphMutable(fightIdsToIds.get(fight.getId)).add(fightIdsToIds.get(fight.getWinFight))
      }
      if (fight.getLoseFight != null) {
        fightsGraphMutable(fightIdsToIds.get(fight.getId)).add(fightIdsToIds.get(fight.getLoseFight))
      }
      stageIdToFightIds(stageIdsToIds.get(fight.getStageId)).add(fight.getId)
    }
    (fightsGraphMutable.map(_.toList), stageIdToFightIds.map(_.toList))
  }

  private def getCompletableFights(fightsInDegree: Array[Int]) = {
    val completableFights = mutable.HashSet.empty[Int]

    fightsInDegree.zipWithIndex.foreach { e =>
      val (degree, fight) = e
      if (degree == 0) {
        completableFights.add(fight)
      }
    }
    completableFights.toSet
  }

  def create(stages: List[StageDescriptorDTO], fights: List[FightDescriptionDTO]): CanFail[StageGraph] = {
    // we resolve transitive connections here (a -> b -> c  ~>  (a -> b, a -> c, b -> c))
    val stageIdsToIds = createEntityIdsToIntIds(stages)
    val stagesGraph = createStagesGraph(stages, stageIdsToIds)

    for {
      stageOrdering <- GraphUtils.findTopologicalOrdering(stagesGraph)
      sortedFights = fights.sortBy {
        _.roundOrZero
      }.sortBy { it => stageOrdering(stageIdsToIds.get(it.getStageId)) }
      fightsMap = sortedFights.groupMapReduce(_.getId)(identity)((a, _) => a)
      fightIdsToIds = createEntityIdsToIntIds(sortedFights)
      (fightsGraph, stageIdsToFightIds) =
        getFightsGraphAndStageIdToFightId(fights, stages.size, fightsMap.size, fightIdsToIds, stageIdsToIds)
      fightsInDegree = GraphUtils.getIndegree(fightsGraph.map(_.toSet))
      fightsOrdering <- GraphUtils.findTopologicalOrdering(fightsGraph)
      completableFights = getCompletableFights(fightsInDegree)
      completedFights = Set.empty[Int]
      categoryIdToFightIds = fights.groupMap(_.getCategoryId)(_.getId)
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
