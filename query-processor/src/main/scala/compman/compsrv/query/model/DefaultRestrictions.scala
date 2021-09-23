package compman.compsrv.query.model

import compman.compsrv.model.dto.competition._

import java.util.UUID

object DefaultRestrictions {
  private val bjj: CategoryRestrictionDTO = new CategoryRestrictionDTO().setId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Value).setName("Sport").setValue("BJJ")

  private def weightRestriction(alias: String = "", maxValue: String, minValue: String = "0"): CategoryRestrictionDTO = new CategoryRestrictionDTO()
    .setId(UUID.randomUUID().toString)
    .setType(CategoryRestrictionType.Range)
    .setName("Weight")
    .setMaxValue(maxValue)
    .setMinValue(minValue)
    .setAlias(alias)
    .setUnit("kg")

  private def ageRestriction(alias: String = "", minValue: String, maxValue: Option[String] = None): CategoryRestrictionDTO = new CategoryRestrictionDTO()
    .setId(UUID.randomUUID().toString)
    .setType(CategoryRestrictionType.Range)
    .setName("Age")
    .setMaxValue(maxValue.getOrElse(minValue))
    .setMinValue(minValue)
    .setAlias(alias)
    .setUnit("y.o.")

  private def beltRestriction(value: String): CategoryRestrictionDTO = new CategoryRestrictionDTO().setId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Value)
    .setValue(value)
    .setName("Belt")


  private def genderRestriction(value: String): CategoryRestrictionDTO = new CategoryRestrictionDTO().setId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Value)
    .setValue(value)
    .setName("Gender")

  private val male = genderRestriction("MALE")
  private val female = genderRestriction("FEMALE")

  private val white = beltRestriction(BeltType.WHITE.name)
  private val gray = beltRestriction(BeltType.GRAY.name)
  private val yellow = beltRestriction(BeltType.YELLOW.name)
  private val orange = beltRestriction(BeltType.ORANGE.name)
  private val green = beltRestriction(BeltType.GREEN.name)
  private val blue = beltRestriction(BeltType.BLUE.name)
  private val purple = beltRestriction(BeltType.PURPLE.name)
  private val brown = beltRestriction(BeltType.BROWN.name)
  private val black = beltRestriction(BeltType.BLACK.name)

  private val adult = ageRestriction(AgeDivisionDTO.ADULT, "18", Some("100"))
  private val junior1 = ageRestriction(AgeDivisionDTO.JUNIOR_I, "10")
  private val junior2 = ageRestriction(AgeDivisionDTO.JUNIOR_II, "11")
  private val junior3 = ageRestriction(AgeDivisionDTO.JUNIOR_III, "12")
  private val juvenile1 = ageRestriction(AgeDivisionDTO.JUVENILE_I, "16")
  private val juvenile2 = ageRestriction(AgeDivisionDTO.JUVENILE_II, "17")
  private val master1 = ageRestriction(AgeDivisionDTO.MASTER_1, "30", Some("100"))
  private val master2 = ageRestriction(AgeDivisionDTO.MASTER_2, "35", Some("100"))
  private val master3 = ageRestriction(AgeDivisionDTO.MASTER_3, "40", Some("100"))
  private val master4 = ageRestriction(AgeDivisionDTO.MASTER_4, "45", Some("100"))
  private val master5 = ageRestriction(AgeDivisionDTO.MASTER_5, "50", Some("100"))
  private val master6 = ageRestriction(AgeDivisionDTO.MASTER_6, "55", Some("100"))
  private val mightyMite1 = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_I, "4")
  private val mightyMite2 = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_II, "5")
  private val mightyMite3 = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_III, "6")
  private val peeWee1 = ageRestriction(AgeDivisionDTO.PEE_WEE_I, "7")
  private val peeWee2 = ageRestriction(AgeDivisionDTO.PEE_WEE_II, "8")
  private val peeWee3 = ageRestriction(AgeDivisionDTO.PEE_WEE_III, "9")
  private val teen1 = ageRestriction(AgeDivisionDTO.TEEN_I, "13")
  private val teen2 = ageRestriction(AgeDivisionDTO.TEEN_II, "14")
  private val teen3 = ageRestriction(AgeDivisionDTO.TEEN_III, "15")

  private val admrooster = weightRestriction(WeightDTO.ROOSTER, "57.5")
  private val admlightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "64")
  private val admfeather = weightRestriction(WeightDTO.FEATHER, "70")
  private val admlight = weightRestriction(WeightDTO.LIGHT, "76")
  private val admmiddle = weightRestriction(WeightDTO.MIDDLE, "82.3")
  private val admmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "88.3")
  private val admheavy = weightRestriction(WeightDTO.HEAVY, "94.3")
  private val admsuperHeavy = weightRestriction(WeightDTO.SUPER_HEAVY, "100.5")
  private val jmrooster = weightRestriction(WeightDTO.ROOSTER, "53.5")

  private val jmlightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "58.5")
  private val jmfeather = weightRestriction(WeightDTO.FEATHER, "64")
  private val jmlight = weightRestriction(WeightDTO.LIGHT, "69")
  private val jmmiddle = weightRestriction(WeightDTO.MIDDLE, "74")
  private val jmmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "79.3")
  private val jmheavy = weightRestriction(WeightDTO.HEAVY, "84.3")
  private val jmsuperHeavy = weightRestriction(WeightDTO.SUPER_HEAVY, "89.3")

  private val multraHeavy = weightRestriction(WeightDTO.ULTRA_HEAVY, "300")
  private val openClass = weightRestriction(WeightDTO.OPEN_CLASS, "300")
  private val adflightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "53.5")
  private val adffeather = weightRestriction(WeightDTO.FEATHER, "58.5")
  private val adflight = weightRestriction(WeightDTO.LIGHT, "64")
  private val adfmiddle = weightRestriction(WeightDTO.MIDDLE, "69")
  private val adfmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "74")
  private val jflightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "48.3")
  private val jffeather = weightRestriction(WeightDTO.FEATHER, "52.5")
  private val jflight = weightRestriction(WeightDTO.LIGHT, "56.5")
  private val jfmiddle = weightRestriction(WeightDTO.MIDDLE, "60.5")
  private val jfmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "65")
  private val fheavy = weightRestriction(WeightDTO.HEAVY, "300")

  val restrictions = List(
    bjj, male, female, white,
    gray, yellow, orange, green, blue,
    purple, brown, black, adult, junior1,
    junior2, junior3, juvenile1, juvenile2,
    master1, master2, master3, master4, master5,
    master6, mightyMite1, mightyMite2, mightyMite3,
    peeWee1, peeWee2, peeWee3, teen1, teen2, teen3,
    admrooster, admlightFeather, admfeather, admlight,
    admmiddle, admmediumHeavy, admheavy, admsuperHeavy,
    jmrooster, jmlightFeather, jmfeather, jmlight,
    jmmiddle, jmmediumHeavy, jmheavy, jmsuperHeavy,
    multraHeavy, openClass, adflightFeather, adffeather, adflight,
    adfmiddle, adfmediumHeavy, jflightFeather,
    jflightFeather, jffeather, jflight, jfmiddle, jfmediumHeavy, fheavy
  )
}
