package compman.compsrv.logic.category

import compman.compsrv.model.commands.payload.{AdjacencyList, GenerateCategoriesFromRestrictionsPayload}
import compman.compsrv.model.dto.competition._

import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object CategoryGenerateService {
  val bjj: CategoryRestrictionDTO = new CategoryRestrictionDTO().setId(UUID.randomUUID().toString)
    .setType(CategoryRestrictionType.Value).setName("Sport").setValue("BJJ")
  private def weightRestriction(alias: String = "", maxValue: String, minValue: String = "0"): CategoryRestrictionDTO =
    new CategoryRestrictionDTO().setId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Range)
      .setName("Weight").setMaxValue(maxValue).setMinValue(minValue).setAlias(alias).setUnit("kg")

  private def ageRestriction(
    alias: String = "",
    minValue: String,
    maxValue: Option[String] = None
  ): CategoryRestrictionDTO = new CategoryRestrictionDTO().setId(UUID.randomUUID().toString)
    .setType(CategoryRestrictionType.Range).setName("Age").setMaxValue(maxValue.getOrElse(minValue))
    .setMinValue(minValue).setAlias(alias).setUnit("y.o.")

  private def beltRestriction(value: String): CategoryRestrictionDTO = new CategoryRestrictionDTO()
    .setId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Value).setValue(value).setName("Belt")

  private def genderRestriction(value: String): CategoryRestrictionDTO = new CategoryRestrictionDTO()
    .setId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Value).setValue(value).setName("Gender")

  def printCategory(cat: CategoryDescriptorDTO): String = {
    import compman.compsrv.model.extension._
    val sv = new StringBuilder()
    if (cat.getRestrictions != null) {
      cat.getRestrictions.foreach { it =>
        sv.append(it.aliasOrName)
        if (it.getValue != null) { sv.append("=").append(it.getValue) }
        sv.append("/")
      }
      sv.deleteCharAt(sv.size - 1)
    }
    sv.toString()
  }

  val male: CategoryRestrictionDTO   = genderRestriction("MALE")
  val female: CategoryRestrictionDTO = genderRestriction("FEMALE")

  val white: CategoryRestrictionDTO  = beltRestriction(BeltType.WHITE.name)
  val gray: CategoryRestrictionDTO   = beltRestriction(BeltType.GRAY.name)
  val yellow: CategoryRestrictionDTO = beltRestriction(BeltType.YELLOW.name)
  val orange: CategoryRestrictionDTO = beltRestriction(BeltType.ORANGE.name)
  val green: CategoryRestrictionDTO  = beltRestriction(BeltType.GREEN.name)
  val blue: CategoryRestrictionDTO   = beltRestriction(BeltType.BLUE.name)
  val purple: CategoryRestrictionDTO = beltRestriction(BeltType.PURPLE.name)
  val brown: CategoryRestrictionDTO  = beltRestriction(BeltType.BROWN.name)
  val black: CategoryRestrictionDTO  = beltRestriction(BeltType.BLACK.name)

  val adult: CategoryRestrictionDTO       = ageRestriction(AgeDivisionDTO.ADULT, "18", Some("100"))
  val junior1: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.JUNIOR_I, "10")
  val junior2: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.JUNIOR_II, "11")
  val junior3: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.JUNIOR_III, "12")
  val juvenile1: CategoryRestrictionDTO   = ageRestriction(AgeDivisionDTO.JUVENILE_I, "16")
  val juvenile2: CategoryRestrictionDTO   = ageRestriction(AgeDivisionDTO.JUVENILE_II, "17")
  val master1: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.MASTER_1, "30", Some("100"))
  val master2: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.MASTER_2, "35", Some("100"))
  val master3: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.MASTER_3, "40", Some("100"))
  val master4: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.MASTER_4, "45", Some("100"))
  val master5: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.MASTER_5, "50", Some("100"))
  val master6: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.MASTER_6, "55", Some("100"))
  val mightyMite1: CategoryRestrictionDTO = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_I, "4")
  val mightyMite2: CategoryRestrictionDTO = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_II, "5")
  val mightyMite3: CategoryRestrictionDTO = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_III, "6")
  val peeWee1: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.PEE_WEE_I, "7")
  val peeWee2: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.PEE_WEE_II, "8")
  val peeWee3: CategoryRestrictionDTO     = ageRestriction(AgeDivisionDTO.PEE_WEE_III, "9")
  val teen1: CategoryRestrictionDTO       = ageRestriction(AgeDivisionDTO.TEEN_I, "13")
  val teen2: CategoryRestrictionDTO       = ageRestriction(AgeDivisionDTO.TEEN_II, "14")
  val teen3: CategoryRestrictionDTO       = ageRestriction(AgeDivisionDTO.TEEN_III, "15")

  val admrooster: CategoryRestrictionDTO      = weightRestriction(WeightDTO.ROOSTER, "57.5")
  val admlightFeather: CategoryRestrictionDTO = weightRestriction(WeightDTO.LIGHT_FEATHER, "64")
  val admfeather: CategoryRestrictionDTO      = weightRestriction(WeightDTO.FEATHER, "70")
  val admlight: CategoryRestrictionDTO        = weightRestriction(WeightDTO.LIGHT, "76")
  val admmiddle: CategoryRestrictionDTO       = weightRestriction(WeightDTO.MIDDLE, "82.3")
  val admmediumHeavy: CategoryRestrictionDTO  = weightRestriction(WeightDTO.MEDIUM_HEAVY, "88.3")
  val admheavy: CategoryRestrictionDTO        = weightRestriction(WeightDTO.HEAVY, "94.3")
  val admsuperHeavy: CategoryRestrictionDTO   = weightRestriction(WeightDTO.SUPER_HEAVY, "100.5")
  val jmrooster: CategoryRestrictionDTO       = weightRestriction(WeightDTO.ROOSTER, "53.5")

  val jmlightFeather: CategoryRestrictionDTO = weightRestriction(WeightDTO.LIGHT_FEATHER, "58.5")
  val jmfeather: CategoryRestrictionDTO      = weightRestriction(WeightDTO.FEATHER, "64")
  val jmlight: CategoryRestrictionDTO        = weightRestriction(WeightDTO.LIGHT, "69")
  val jmmiddle: CategoryRestrictionDTO       = weightRestriction(WeightDTO.MIDDLE, "74")
  val jmmediumHeavy: CategoryRestrictionDTO  = weightRestriction(WeightDTO.MEDIUM_HEAVY, "79.3")
  val jmheavy: CategoryRestrictionDTO        = weightRestriction(WeightDTO.HEAVY, "84.3")
  val jmsuperHeavy: CategoryRestrictionDTO   = weightRestriction(WeightDTO.SUPER_HEAVY, "89.3")

  val multraHeavy: CategoryRestrictionDTO     = weightRestriction(WeightDTO.ULTRA_HEAVY, "300")
  val openClass: CategoryRestrictionDTO       = weightRestriction(WeightDTO.OPEN_CLASS, "300")
  val adflightFeather: CategoryRestrictionDTO = weightRestriction(WeightDTO.LIGHT_FEATHER, "53.5")
  val adffeather: CategoryRestrictionDTO      = weightRestriction(WeightDTO.FEATHER, "58.5")
  val adflight: CategoryRestrictionDTO        = weightRestriction(WeightDTO.LIGHT, "64")
  val adfmiddle: CategoryRestrictionDTO       = weightRestriction(WeightDTO.MIDDLE, "69")
  val adfmediumHeavy: CategoryRestrictionDTO  = weightRestriction(WeightDTO.MEDIUM_HEAVY, "74")

  val jflightFeather: CategoryRestrictionDTO = weightRestriction(WeightDTO.LIGHT_FEATHER, "48.3")
  val jffeather: CategoryRestrictionDTO      = weightRestriction(WeightDTO.FEATHER, "52.5")
  val jflight: CategoryRestrictionDTO        = weightRestriction(WeightDTO.LIGHT, "56.5")
  val jfmiddle: CategoryRestrictionDTO       = weightRestriction(WeightDTO.MIDDLE, "60.5")
  val jfmediumHeavy: CategoryRestrictionDTO  = weightRestriction(WeightDTO.MEDIUM_HEAVY, "65")
  val fheavy: CategoryRestrictionDTO         = weightRestriction(WeightDTO.HEAVY, "300")

  val restrictions = List(
    bjj,
    male,
    female,
    white,
    gray,
    yellow,
    orange,
    green,
    blue,
    purple,
    brown,
    black,
    adult,
    junior1,
    junior2,
    junior3,
    juvenile1,
    juvenile2,
    master1,
    master2,
    master3,
    master4,
    master5,
    master6,
    mightyMite1,
    mightyMite2,
    mightyMite3,
    peeWee1,
    peeWee2,
    peeWee3,
    teen1,
    teen2,
    teen3,
    admrooster,
    admlightFeather,
    admfeather,
    admlight,
    admmiddle,
    admmediumHeavy,
    admheavy,
    admsuperHeavy,
    jmrooster,
    jmlightFeather,
    jmfeather,
    jmlight,
    jmmiddle,
    jmmediumHeavy,
    jmheavy,
    jmsuperHeavy,
    multraHeavy,
    openClass,
    adflightFeather,
    adffeather,
    adflight,
    adfmiddle,
    adfmediumHeavy,
    jflightFeather,
    jflightFeather,
    jffeather,
    jflight,
    jfmiddle,
    jfmediumHeavy,
    fheavy
  )

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
