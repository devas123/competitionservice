package compman.compsrv.logic.schedule

import com.google.common.collect.{BiMap, HashBiMap}
import compman.compsrv.Utils
import compman.compsrv.logic.fight.CanFail
import compman.compsrv.logic.schedule.GraphUtils.OrderingTypes
import compman.compsrv.model.extensions._
import compservice.model.protobuf.model._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class StageGraph private[schedule] (
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

  private def createInternalStagesGraphRepresentation(
    stages: DiGraph,
    stageIdsToIds: BiMap[String, Int]
  ): Array[List[Int]] = {
    val stageNodesMutable = Array.fill(stageIdsToIds.keySet().size()) { mutable.HashSet.empty[Int] }
    stages.outgoingConnections.foreach { case (stageId, outgoingCollections) =>
      val parentId = stageId
      if (stageIdsToIds.containsKey(parentId)) {
        outgoingCollections.ids.foreach { nodeId =>
          stageNodesMutable(stageIdsToIds.get(parentId)).add(stageIdsToIds.get(nodeId))
        }
      }
    }
    stageNodesMutable.map { _.toList }
  }

  def createStagesDigraph(stages: Iterable[StageDescriptor]): DiGraph = {
    val incomingConnections = createMutableMapOfStringToList
    val outgoingConnections = createMutableMapOfStringToList
    for (stage <- stages) {
      incomingConnections.put(stage.id, mutable.ArrayBuffer.empty)
      outgoingConnections.put(stage.id, mutable.ArrayBuffer.empty)
    }
    for (
      stage    <- stages;
      selector <- stage.inputDescriptor.map(_.selectors).getOrElse(Seq.empty)
    ) {
      val parentId = selector.applyToStageId
      if (parentId.nonEmpty) {
        incomingConnections(stage.id).append(parentId)
        outgoingConnections.getOrElseUpdate(parentId, mutable.ArrayBuffer.empty).append(stage.id)
      }
    }
    diGraph(incomingConnections, outgoingConnections)
  }

  def diGraph(
    incomingConnections: mutable.HashMap[String, ArrayBuffer[String]],
    outgoingConnections: mutable.HashMap[String, ArrayBuffer[String]]
  ): DiGraph = {
    DiGraph().withIncomingConnections(incomingConnections.view.mapValues(v => IdList(v.distinct.toSeq)).toMap)
      .withOutgoingConnections(outgoingConnections.view.mapValues(v => IdList(v.distinct.toSeq)).toMap)
  }

  def mergeDigraphs(a: DiGraph, b: DiGraph): DiGraph = {
    val aIncoming = a.incomingConnections
    val bIncoming = b.incomingConnections

    val aOutgoing = a.outgoingConnections
    val bOutgoing = b.outgoingConnections

    val incomingConnections = createMutableMapOfStringToList
    val outgoingConnections = createMutableMapOfStringToList

    addAllEdges(aIncoming, incomingConnections)
    addAllEdges(aOutgoing, outgoingConnections)

    for (elem <- bIncoming) {
      incomingConnections.getOrElseUpdate(elem._1, mutable.ArrayBuffer.empty).addAll(elem._2.ids)
    }
    for (elem <- bOutgoing) {
      outgoingConnections.getOrElseUpdate(elem._1, mutable.ArrayBuffer.empty).addAll(elem._2.ids)
    }
    diGraph(incomingConnections, outgoingConnections)
  }

  def removeStage(graph: DiGraph, stageId: String): DiGraph = {
    val stageOutgoing = graph.outgoingConnections.get(stageId).map(_.ids).getOrElse(Seq.empty)
    val stageIncoming = graph.incomingConnections.get(stageId).map(_.ids).getOrElse(Seq.empty)

    val outgoingMutable: mutable.HashMap[String, IdList] = mutable.HashMap.newBuilder(graph.outgoingConnections)
      .result()
    val incomingMutable: mutable.HashMap[String, IdList] = mutable.HashMap.newBuilder(graph.incomingConnections)
      .result()

    removeStageFromConnectionReverseAdjList(stageId, stageIncoming, outgoingMutable)
    removeStageFromConnectionReverseAdjList(stageId, stageOutgoing, incomingMutable)

    graph.withOutgoingConnections(outgoingMutable.toMap - stageId)
      .withIncomingConnections(incomingMutable.toMap - stageId)
  }

  private def removeStageFromConnectionReverseAdjList(
    stageId: String,
    vertices: Seq[String],
    adjacencyList: mutable.HashMap[String, IdList]
  ): Unit = {
    for (id <- vertices) {
      adjacencyList.updateWith(id)(existing => existing.map(idList => idList.withIds(idList.ids.filter(_ != stageId))))
    }
  }

  private def addAllEdges(
    aIncoming: Map[String, IdList],
    incomingConnections: mutable.HashMap[String, ArrayBuffer[String]]
  ) = { incomingConnections.addAll(aIncoming.iterator.map(e => (e._1, mutable.ArrayBuffer(e._2.ids: _*)))) }

  def createMutableMapOfStringToList = mutable.HashMap.empty[String, ArrayBuffer[String]]

  private def createStageIdsToIntIds(stages: List[StageDescriptor]) = {
    val indices = stages.distinctBy(_.id).zipWithIndex.map(p => (p._1.id, p._2)).toMap
    HashBiMap.create(indices.asJava)
  }
  private def createFightIdsToIntIds(fights: List[FightDescription]) = {
    HashBiMap.create(fights.distinctBy(_.id).zipWithIndex.map(p => (p._1.id, p._2)).toMap.asJava)
  }

  private def isNotUncompletable(f: FightDescription) = f.status != FightStatus.UNCOMPLETABLE

  private def getFightsGraphAndStageIdToFightId(
    fights: Map[String, FightDescription],
    stagesNumber: Int,
    fightsNumber: Int,
    fightIdsToIds: BiMap[String, Int],
    stageIdsToIds: BiMap[String, Int]
  ) = {
    val fightsGraphMutable = Array.fill(fightsNumber) { mutable.HashSet.empty[Int] }
    val stageIdToFightIds  = Array.fill(stagesNumber) { mutable.HashSet.empty[String] }

    fights.values.filter(isNotUncompletable).foreach { fight =>
      fight.scores.foreach { compScore =>
        if (
          compScore.parentFightId.isDefined &&
          fights.get(compScore.parentFightId.get)
            .exists(isNotUncompletable) // Not including UNCOMPLETABLE fights to fights graph
        ) { fightsGraphMutable(fightIdsToIds.get(compScore.parentFightId)).add(fightIdsToIds.get(fight.id)) }
      }
      stageIdToFightIds(stageIdsToIds.get(fight.stageId)).add(fight.id)
    }
    (fightsGraphMutable.map(_.toList), stageIdToFightIds.map(_.toList))
  }

  private def getCompletableFights(fightsInDegree: Array[Int]) = {
    val completableFights = mutable.HashSet.empty[Int]
    fightsInDegree.zipWithIndex.foreach { case (degree, fight) => if (degree == 0) { completableFights.add(fight) } }
    completableFights.toSet
  }

  def create(
    stages: List[StageDescriptor],
    stageDiGraph: DiGraph,
    fights: List[FightDescription]
  ): CanFail[StageGraph] = {
    // we resolve transitive connections here (a -> b -> c  ~>  (a -> b, a -> c, b -> c))
    val stageIdsToIds = createStageIdsToIntIds(stages)
    val stagesGraph   = createInternalStagesGraphRepresentation(stageDiGraph, stageIdsToIds)

    for {
      stageOrdering <- GraphUtils.findTopologicalOrdering(stagesGraph, OrderingTypes.Stages)
      sortedFights = fights.filter(isNotUncompletable).sortBy { _.roundOrZero }.sortBy { it =>
        stageOrdering(stageIdsToIds.get(it.stageId))
      }
      fightsMap     = Utils.groupById(sortedFights)(_.id)
      fightIdsToIds = createFightIdsToIntIds(sortedFights)
      (fightsGraph, stageIdsToFightIds) =
        getFightsGraphAndStageIdToFightId(fightsMap, stages.size, fightsMap.size, fightIdsToIds, stageIdsToIds)
      fightsInDegree = GraphUtils.getIndegree(fightsGraph)
      fightsOrdering <- GraphUtils.findTopologicalOrdering(fightsGraph, OrderingTypes.Fights)
      completableFights    = getCompletableFights(fightsInDegree)
      completedFights      = Set.empty[Int]
      categoryIdToFightIds = sortedFights.groupMap(_.categoryId)(_.id).map { case (k, list) => k -> list.toSet }
    } yield new StageGraph(
      stagesGraph = stagesGraph,
      fightsGraph = fightsGraph,
      stageIdsToIds = stageIdsToIds,
      fightIdsToIds = fightIdsToIds,
      fightsMap = fightsMap,
      stageOrdering = stageOrdering,
      fightsOrdering = fightsOrdering,
      nonCompleteCount = fightsMap.values.count(isNotUncompletable),
      fightsInDegree = fightsInDegree,
      completedFights = completedFights,
      completableFights = completableFights,
      categoryIdToFightIds = categoryIdToFightIds,
      stageIdsToFightIds = stageIdsToFightIds
    )
  }

}
