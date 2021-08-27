package compman.compsrv.logic.service.schedule

import cats.implicits._
import com.google.common.collect.{BiMap, HashBiMap}
import compman.compsrv.logic.service.fights.CanFail
import compman.compsrv.model.dto.schedule.{ScheduleRequirementDTO, ScheduleRequirementType}
import compman.compsrv.model.extension._
import compman.compsrv.model.Errors

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class RequirementsGraph private[schedule] (
  private val requirementIdToId: BiMap[String, Int] = HashBiMap.create(),
  private val graph: Array[List[Int]],
  private val categoryRequirements: Map[String, List[Int]],
  private val requirementFightIds: Array[Set[String]],
  orderedRequirements: Array[ScheduleRequirementDTO],
  requirementFightsSize: Array[Int],
  private val size: Int
) {
  def getFightIdsForRequirement(reqirementId: String): Set[String] = Option(requirementIdToId.get(reqirementId))
    .flatMap(id => if (id >= 0 && id < requirementFightIds.length) Option(requirementFightIds(id)) else None)
    .getOrElse(Set.empty)
  def getIndex(id: String): CanFail[Int] = {
    Option(requirementIdToId.get(id)).toRight(Errors.InternalError(s"Requirement's $id index not found"))
  }
  def getIndexOrMinus1(id: String): Int = getIndex(id).getOrElse(-1)
}

object RequirementsGraph {
  def create(
    requirements: Map[String, ScheduleRequirementDTO],
    categoryIdToFightIds: Map[String, Set[String]],
    periods: Array[String]
  ): CanFail[RequirementsGraph] = {

    val requirementIdToId: BiMap[String, Int] = HashBiMap.create()
    val fightIdToCategoryId                   = mutable.Map.empty[String, String]
    categoryIdToFightIds.foreach { case (t, u) => u.foreach { fid => fightIdToCategoryId(fid) = t } }
    var n = 0
    val sorted = requirements.values.toList.sortBy { _.getEntryOrder }.sortBy { it => periods.indexOf(it.getPeriodId) }
      .toArray
    sorted.foreach { req =>
      if (!requirementIdToId.containsKey(req.getId)) {
        requirementIdToId.put(req.getId, n)
        n += 1
      }
    }
    val categoryRequirements = mutable.Map.empty[String, ArrayBuffer[Int]]
    val requirementsGraph    = Array.fill(n) { mutable.HashSet[Int]() }
    val requirementFightIds  = Array.fill(n) { mutable.HashSet[String]() }
    for (i <- sorted.indices) {
      val r  = sorted(i)
      val id = requirementIdToId.get(r.getId)

      val categories =
        (r.categories.map(_.toList), r.fightIds.flatMap(_.toList.traverse(it => fightIdToCategoryId.get(it))))
          .mapN((a, b) => a <+> b).map(_.toSet).getOrElse(Set.empty)
      categories.foreach { it => categoryRequirements.getOrElseUpdate(it, ArrayBuffer.empty).append(id) }
      if (i > 0) { requirementsGraph(requirementIdToId.get(sorted(i - 1).getId)).add(id) }
    }

    categoryRequirements.foreach { case (key, value) =>
      val dispatchedFights = value.map { ri => requirements(requirementIdToId.inverse().get(ri)) }.filter { it =>
        it.getEntryType == ScheduleRequirementType.FIGHTS
      }.flatMap { _.fightIdsOrEmpty }.toSet
      value.foreach { ri =>
        val r = requirements(requirementIdToId.inverse().get(ri))
        if (r.getEntryType == ScheduleRequirementType.FIGHTS) { requirementFightIds(ri).addAll(r.fightIdsOrEmpty) }
        else if (r.getEntryType == ScheduleRequirementType.CATEGORIES) {
          requirementFightIds(ri).addAll(categoryIdToFightIds.getOrElse(key, Set.empty) -- dispatchedFights)
        }
      }
    }

    val requirementFightsSize = requirementFightIds.map(_.size)

    val size = n
    for {
      ordering <- GraphUtils.findTopologicalOrdering(requirementsGraph.map(_.toList))
      graph               = requirementsGraph.map { _.toList }
      orderedRequirements = sorted.sortBy { it => ordering(requirementIdToId.get(it.getId)) }
    } yield RequirementsGraph(
      requirementIdToId = requirementIdToId,
      graph = graph,
      categoryRequirements = categoryRequirements.view.mapValues(_.toList).toMap,
      requirementFightIds = requirementFightIds.map(_.toSet),
      orderedRequirements = orderedRequirements,
      requirementFightsSize = requirementFightsSize,
      size = size
    )
  }
}
