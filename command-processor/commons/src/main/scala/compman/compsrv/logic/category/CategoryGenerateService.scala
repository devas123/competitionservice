package compman.compsrv.logic.category

import compman.compsrv.Utils
import compservice.model.protobuf.commandpayload.{AdjacencyList, GenerateCategoriesFromRestrictionsPayload}
import compservice.model.protobuf.model._

import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object CategoryGenerateService {

  import cats.implicits._
  def createCategory(
                      restrictions: List[CategoryRestriction],
                      registrationOpen: Boolean = true
  ): CategoryDescriptor = {
    val restr = restrictions.mapWithIndex { case (it, index) => it.withRestrictionOrder(index) }
    CategoryDescriptor().withId(UUID.randomUUID().toString)
      .withRestrictions(restr)
      .withRegistrationOpen(registrationOpen)
  }

  def generateCategories(payload: GenerateCategoriesFromRestrictionsPayload): List[CategoryDescriptor] = {
    payload
      .idTrees.flatMap { idTree =>
      val restrNamesOrder = payload.restrictionNames.toList.mapWithIndex { case (a, i) => (a, i) }.toMap
      generateCategoriesFromRestrictions(payload.restrictions.toArray, idTree, restrNamesOrder)
    }.toList
  }

  case class AdjacencyListEntryWithLevelAndId(id: Int, entry: CategoryRestriction, level: Int)

  def generateCategoriesFromRestrictions(
    restrictions: Array[CategoryRestriction],
    idTree: AdjacencyList,
    restrictionNamesOrder: Map[String, Int]
  ): List[CategoryDescriptor] = {
    val stack = mutable.Stack[AdjacencyListEntryWithLevelAndId]()
    val idMap = Utils.groupById(idTree.vertices)(_.id)
    stack.push(AdjacencyListEntryWithLevelAndId(idTree.root, restrictions(idTree.root), 0))
    val paths       = ArrayBuffer.empty[Seq[CategoryRestriction]]
    val currentPath = mutable.ArrayDeque.empty[CategoryRestriction]
    while (stack.nonEmpty) {
      val node = stack.pop()
      while (currentPath.size > node.level) { currentPath.removeLast() }
      currentPath.append(node.entry)
      val children = idMap.get(node.id).flatMap(n => Option(n.children))
      if (children.isDefined) {
        children.foreach(_.foreach { it =>
          stack.push(AdjacencyListEntryWithLevelAndId(it, restrictions(it), node.level + 1))
        })
      } else {
        paths.append(
          currentPath.sortBy(categoryRestrictionDTO => restrictionNamesOrder(categoryRestrictionDTO.name)).toList
            .mapWithIndex { case (c, index) => c.withRestrictionOrder(index) }
        )
      }
    }
    paths.toList.map { path =>
      CategoryDescriptor()
        .withRegistrationOpen(false)
        .withId(UUID.randomUUID().toString)
        .withRestrictions(path)
    }
  }

}
