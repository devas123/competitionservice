package compman.compsrv.logic.category

import compman.compsrv.model.commands.payload.{AdjacencyList, GenerateCategoriesFromRestrictionsPayload}
import compman.compsrv.model.dto.competition._

import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object CategoryGenerateService {

  import cats.implicits._
  def createCategory(
    restrictions: List[CategoryRestrictionDTO],
    registrationOpen: Boolean = true
  ): CategoryDescriptorDTO = {
    val restr = restrictions.mapWithIndex { case (it, index) => it.setRestrictionOrder(index) }
    new CategoryDescriptorDTO().setId(UUID.randomUUID().toString).setRestrictions(restr.toArray)
      .setRegistrationOpen(registrationOpen)
  }

  def generateCategories(payload: GenerateCategoriesFromRestrictionsPayload): List[CategoryDescriptorDTO] = {
    payload.getIdTrees.flatMap { idTree =>
      val restrNamesOrder = payload.getRestrictionNames.toList.mapWithIndex { case (a, i) => (a, i) }.toMap
      generateCategoriesFromRestrictions(payload.getRestrictions, idTree, restrNamesOrder)
    }.toList
  }

  case class AdjacencyListEntryWithLevelAndId(id: Int, entry: CategoryRestrictionDTO, level: Int)

  def generateCategoriesFromRestrictions(
    restrictions: Array[CategoryRestrictionDTO],
    idTree: AdjacencyList,
    restrictionNamesOrder: Map[String, Int]
  ): List[CategoryDescriptorDTO] = {
    val stack = mutable.Stack[AdjacencyListEntryWithLevelAndId]()
    val idMap = idTree.getVertices.groupMapReduce(_.getId)(identity)((a, _) => a)
    stack.push(AdjacencyListEntryWithLevelAndId(idTree.getRoot, restrictions(idTree.getRoot), 0))
    val paths       = ArrayBuffer.empty[Array[CategoryRestrictionDTO]]
    val currentPath = mutable.ArrayDeque.empty[CategoryRestrictionDTO]
    while (stack.nonEmpty) {
      val node = stack.pop()
      while (currentPath.size > node.level) { currentPath.removeLast() }
      currentPath.append(node.entry)
      val children = idMap.get(node.id).flatMap(n => Option(n.getChildren))
      if (children.isDefined) {
        children.foreach(_.foreach { it =>
          stack.push(AdjacencyListEntryWithLevelAndId(it, restrictions(it), node.level + 1))
        })
      } else {
        paths.append(
          currentPath.sortBy(categoryRestrictionDTO => restrictionNamesOrder(categoryRestrictionDTO.getName)).toList
            .mapWithIndex { case (c, index) => c.setRestrictionOrder(index) }.toArray
        )
      }
    }
    paths.toList.map { path =>
      new CategoryDescriptorDTO().setRegistrationOpen(false).setId(UUID.randomUUID().toString).setRestrictions(path)
    }
  }

}
