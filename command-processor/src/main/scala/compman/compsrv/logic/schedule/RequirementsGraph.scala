package compman.compsrv.logic.schedule

import cats.implicits._
import com.google.common.collect.{BiMap, HashBiMap}
import compman.compsrv.logic.fight.CanFail
import compman.compsrv.model.extensions._
import compman.compsrv.model.Errors
import compservice.model.protobuf.model._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class RequirementsGraph private[schedule](
  private val requirementIdToId: BiMap[String, Int] = HashBiMap.create(),
  private val graph: Seq[List[Int]],
  private val categoryRequirements: Map[String, List[Int]],
  private val requirementFightIds: Seq[Set[String]],
  orderedRequirements: Seq[ScheduleRequirement],
  requirementFightsSize: Seq[Int],
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
  def apply(
    requirements: Map[String, ScheduleRequirement],
    categoryIdToFightIds: Map[String, Set[String]],
    periods: Seq[String]
  ): CanFail[RequirementsGraph] = {

    val requirementIdToId: BiMap[String, Int] = HashBiMap.create()
    val fightIdToCategoryId                   = mutable.Map.empty[String, String]
    categoryIdToFightIds.foreach { case (t, u) => u.foreach { fid => fightIdToCategoryId(fid) = t } }
    var n = 0
    val sorted = requirements.values.toList.sortBy { _.entryOrder }.sortBy { it => periods.indexOf(it.periodId) }

    sorted.foreach { req =>
      if (!requirementIdToId.containsKey(req.id)) {
        requirementIdToId.put(req.id, n)
        n += 1
      }
    }
    val categoryRequirements = mutable.Map.empty[String, ArrayBuffer[Int]]
    val requirementsGraph    = Array.fill(n) { mutable.HashSet[Int]() }
    val requirementFightIds  = Array.fill(n) { mutable.HashSet[String]() }
    for (i <- sorted.indices) {
      val r  = sorted(i)
      val id = requirementIdToId.get(r.id)

      val categories = (r.categoryIds ++ r.fightIds.mapFilter(it => fightIdToCategoryId.get(it))).toSet

      categories.foreach { it => categoryRequirements.getOrElseUpdate(it, ArrayBuffer.empty).append(id) }
      if (i > 0) { requirementsGraph(requirementIdToId.get(sorted(i - 1).id)).add(id) }
    }

    categoryRequirements.foreach { case (key, value) =>
      val dispatchedFights = value.map { ri => requirements(requirementIdToId.inverse().get(ri)) }.filter { it =>
        it.entryType == ScheduleRequirementType.FIGHTS
      }.flatMap { _.fightIdsOrEmpty }.toSet
      value.foreach { ri =>
        val r = requirements(requirementIdToId.inverse().get(ri))
        if (r.entryType == ScheduleRequirementType.FIGHTS) { requirementFightIds(ri).addAll(r.fightIdsOrEmpty) }
        else if (r.entryType == ScheduleRequirementType.CATEGORIES) {
          requirementFightIds(ri).addAll(categoryIdToFightIds.getOrElse(key, Set.empty) -- dispatchedFights)
        }
      }
    }


    val size = n
    for {
      ordering <- GraphUtils.findTopologicalOrdering(requirementsGraph.map(_.toList))
      graph               = requirementsGraph.map { _.toList }
      orderedRequirements = sorted.sortBy { it => ordering(requirementIdToId.get(it.id)) }
    } yield RequirementsGraph(
      requirementIdToId = requirementIdToId,
      graph = graph.toSeq,
      categoryRequirements = categoryRequirements.view.mapValues(_.toList).toMap,
      requirementFightIds = requirementFightIds.map(_.toSet).toSeq,
      orderedRequirements = orderedRequirements,
      requirementFightsSize = requirementFightIds.map(_.size).toSeq,
      size = size
    )
  }
}
