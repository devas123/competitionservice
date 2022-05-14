package compman.compsrv.query.model

import compman.compsrv.model.dto.competition._

import java.util.UUID

object BeltType extends Enumeration {
  type BeltType = Value
  val WHITE, GRAY, YELLOW, ORANGE, GREEN, BLUE, PURPLE, BROWN, BLACK = Value
}

object Weight {
  val ROOSTER       = "Rooster"
  val LIGHT_FEATHER = "LightFeather"
  val FEATHER       = "Feather"
  val LIGHT         = "Light"
  val MIDDLE        = "Middle"
  val MEDIUM_HEAVY  = "Medium Heavy"
  val HEAVY         = "Heavy"
  val SUPER_HEAVY   = "Super Heavy"
  val ULTRA_HEAVY   = "Ultra Heavy"
  val OPEN_CLASS    = "Open class"
  val WEIGHT_NAMES: Array[String] =
    Array(ROOSTER, LIGHT_FEATHER, FEATHER, LIGHT, MIDDLE, MEDIUM_HEAVY, HEAVY, SUPER_HEAVY, ULTRA_HEAVY, OPEN_CLASS)
}

object AgeDivision {
  val MIGHTY_MITE_I   = "MIGHTY MITE I"
  val MIGHTY_MITE_II  = "MIGHTY MITE II"
  val MIGHTY_MITE_III = "MIGHTY MITE III"
  val PEE_WEE_I       = "PEE WEE I"
  val PEE_WEE_II      = "PEE WEE II"
  val PEE_WEE_III     = "PEE WEE III"
  val JUNIOR_I        = "JUNIOR I"
  val JUNIOR_II       = "JUNIOR II"
  val JUNIOR_III      = "JUNIOR III"
  val TEEN_I          = "TEEN I"
  val TEEN_II         = "TEEN II"
  val TEEN_III        = "TEEN III"
  val JUVENILE_I      = "JUVENILE I"
  val JUVENILE_II     = "JUVENILE II"
  val ADULT           = "ADULT"
  val MASTER_1        = "MASTER 1"
  val MASTER_2        = "MASTER 2"
  val MASTER_3        = "MASTER 3"
  val MASTER_4        = "MASTER 4"
  val MASTER_5        = "MASTER 5"
  val MASTER_6        = "MASTER 6"
}

object DefaultRestrictions {
  private val bjj = new CategoryRestrictionDTO().setRestrictionId(UUID.randomUUID().toString)
    .setType(CategoryRestrictionType.Value).setName("Sport").setValue("BJJ")

  private def weightRestriction(alias: String, maxValue: String, minValue: String = "0") = new CategoryRestrictionDTO()
    .setRestrictionId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Range).setName("Weight")
    .setMaxValue(maxValue).setMinValue(minValue).setAlias(alias).setUnit("kg")

  private def ageRestriction(alias: String, minValue: String, maxValue: Option[String] = None) =
    new CategoryRestrictionDTO().setRestrictionId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Range)
      .setName("Age").setMaxValue(maxValue.getOrElse(minValue)).setMinValue(minValue).setAlias(alias).setUnit("y.o.")

  private def beltRestriction(value: String) = new CategoryRestrictionDTO().setRestrictionId(UUID.randomUUID().toString)
    .setType(CategoryRestrictionType.Value).setValue(value).setName("Belt")

  private def genderRestriction(value: String) = new CategoryRestrictionDTO()
    .setRestrictionId(UUID.randomUUID().toString).setType(CategoryRestrictionType.Value).setValue(value)
    .setName("Gender")

  private val male   = genderRestriction("MALE")
  private val female = genderRestriction("FEMALE")

  private val white  = beltRestriction(BeltType.WHITE.toString)
  private val gray   = beltRestriction(BeltType.GRAY.toString)
  private val yellow = beltRestriction(BeltType.YELLOW.toString)
  private val orange = beltRestriction(BeltType.ORANGE.toString)
  private val green  = beltRestriction(BeltType.GREEN.toString)
  private val blue   = beltRestriction(BeltType.BLUE.toString)
  private val purple = beltRestriction(BeltType.PURPLE.toString)
  private val brown  = beltRestriction(BeltType.BROWN.toString)
  private val black  = beltRestriction(BeltType.BLACK.toString)

  private val adult       = ageRestriction(AgeDivision.ADULT, "18", Some("100"))
  private val junior1     = ageRestriction(AgeDivision.JUNIOR_I, "10")
  private val junior2     = ageRestriction(AgeDivision.JUNIOR_II, "11")
  private val junior3     = ageRestriction(AgeDivision.JUNIOR_III, "12")
  private val juvenile1   = ageRestriction(AgeDivision.JUVENILE_I, "16")
  private val juvenile2   = ageRestriction(AgeDivision.JUVENILE_II, "17")
  private val master1     = ageRestriction(AgeDivision.MASTER_1, "30", Some("100"))
  private val master2     = ageRestriction(AgeDivision.MASTER_2, "35", Some("100"))
  private val master3     = ageRestriction(AgeDivision.MASTER_3, "40", Some("100"))
  private val master4     = ageRestriction(AgeDivision.MASTER_4, "45", Some("100"))
  private val master5     = ageRestriction(AgeDivision.MASTER_5, "50", Some("100"))
  private val master6     = ageRestriction(AgeDivision.MASTER_6, "55", Some("100"))
  private val mightyMite1 = ageRestriction(AgeDivision.MIGHTY_MITE_I, "4")
  private val mightyMite2 = ageRestriction(AgeDivision.MIGHTY_MITE_II, "5")
  private val mightyMite3 = ageRestriction(AgeDivision.MIGHTY_MITE_III, "6")
  private val peeWee1     = ageRestriction(AgeDivision.PEE_WEE_I, "7")
  private val peeWee2     = ageRestriction(AgeDivision.PEE_WEE_II, "8")
  private val peeWee3     = ageRestriction(AgeDivision.PEE_WEE_III, "9")
  private val teen1       = ageRestriction(AgeDivision.TEEN_I, "13")
  private val teen2       = ageRestriction(AgeDivision.TEEN_II, "14")
  private val teen3       = ageRestriction(AgeDivision.TEEN_III, "15")

  private val admrooster      = weightRestriction(Weight.ROOSTER, "57.5")
  private val admlightFeather = weightRestriction(Weight.LIGHT_FEATHER, "64")
  private val admfeather      = weightRestriction(Weight.FEATHER, "70")
  private val admlight        = weightRestriction(Weight.LIGHT, "76")
  private val admmiddle       = weightRestriction(Weight.MIDDLE, "82.3")
  private val admmediumHeavy  = weightRestriction(Weight.MEDIUM_HEAVY, "88.3")
  private val admheavy        = weightRestriction(Weight.HEAVY, "94.3")
  private val admsuperHeavy   = weightRestriction(Weight.SUPER_HEAVY, "100.5")
  private val jmrooster       = weightRestriction(Weight.ROOSTER, "53.5")

  private val jmlightFeather = weightRestriction(Weight.LIGHT_FEATHER, "58.5")
  private val jmfeather      = weightRestriction(Weight.FEATHER, "64")
  private val jmlight        = weightRestriction(Weight.LIGHT, "69")
  private val jmmiddle       = weightRestriction(Weight.MIDDLE, "74")
  private val jmmediumHeavy  = weightRestriction(Weight.MEDIUM_HEAVY, "79.3")
  private val jmheavy        = weightRestriction(Weight.HEAVY, "84.3")
  private val jmsuperHeavy   = weightRestriction(Weight.SUPER_HEAVY, "89.3")

  private val multraHeavy     = weightRestriction(Weight.ULTRA_HEAVY, "300")
  private val openClass       = weightRestriction(Weight.OPEN_CLASS, "300")
  private val adflightFeather = weightRestriction(Weight.LIGHT_FEATHER, "53.5")
  private val adffeather      = weightRestriction(Weight.FEATHER, "58.5")
  private val adflight        = weightRestriction(Weight.LIGHT, "64")
  private val adfmiddle       = weightRestriction(Weight.MIDDLE, "69")
  private val adfmediumHeavy  = weightRestriction(Weight.MEDIUM_HEAVY, "74")
  private val jflightFeather  = weightRestriction(Weight.LIGHT_FEATHER, "48.3")
  private val jffeather       = weightRestriction(Weight.FEATHER, "52.5")
  private val jflight         = weightRestriction(Weight.LIGHT, "56.5")
  private val jfmiddle        = weightRestriction(Weight.MIDDLE, "60.5")
  private val jfmediumHeavy   = weightRestriction(Weight.MEDIUM_HEAVY, "65")
  private val fheavy          = weightRestriction(Weight.HEAVY, "300")

  val restrictions: Seq[CategoryRestrictionDTO] = List(
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
}
