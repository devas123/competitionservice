package compman.compsrv.service

import compman.compsrv.model.competition.*
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class CategoryGeneratorService {
    fun createDefaultBjjCategories(competitionId: String): List<Category> {
        val adultMaleRooster = Category(BjjAgeDivisions.ADULT, Gender.MALE, competitionId, UUID.randomUUID().toString(), Weight(Weight.ROOSTER, BigDecimal("57.5")), BeltType.WHITE, BigDecimal(5))
        val adultMaleLightFeather = adultMaleRooster.setWeight(Weight(Weight.LIGHT_FEATHER, BigDecimal("64")))
        val adultMaleFeather = adultMaleRooster.setWeight(Weight(Weight.FEATHER, BigDecimal("70")))
        val adultMaleLight = adultMaleRooster.setWeight(Weight(Weight.LIGHT, BigDecimal("76")))
        val adultMaleMiddle = adultMaleRooster.setWeight(Weight(Weight.MIDDLE, BigDecimal("82.3")))
        val adultMaleMiddleHeavy = adultMaleRooster.setWeight(Weight(Weight.MEDIUM_HEAVY, BigDecimal("88.3")))
        val adultMaleHeavy = adultMaleRooster.setWeight(Weight(Weight.HEAVY, BigDecimal("94.3")))
        val adultMaleSuperHeavy = adultMaleRooster.setWeight(Weight(Weight.SUPER_HEAVY, BigDecimal("100.5")))
        val adultMaleUltraHeavy = adultMaleRooster.setWeight(Weight(Weight.ULTRA_HEAVY, BigDecimal("300")))

        val adultFemaleRooster = Category(BjjAgeDivisions.ADULT, Gender.FEMALE, competitionId, UUID.randomUUID().toString(), Weight(Weight.ROOSTER, BigDecimal("48.5")), BeltType.WHITE, BigDecimal(5))
        val adultFemaleLightFeather = adultFemaleRooster.setWeight(Weight(Weight.LIGHT_FEATHER, BigDecimal("53.5")))
        val adultFemaleFeather = adultFemaleRooster.setWeight(Weight(Weight.FEATHER, BigDecimal("58.5")))
        val adultFemaleLight = adultFemaleRooster.setWeight(Weight(Weight.LIGHT, BigDecimal("64")))
        val adultFemaleMiddle = adultFemaleRooster.setWeight(Weight(Weight.MIDDLE, BigDecimal("69")))
        val adultFemaleMiddleHeavy = adultFemaleRooster.setWeight(Weight(Weight.MEDIUM_HEAVY, BigDecimal("74")))
        val adultFemaleHeavy = adultFemaleRooster.setWeight(Weight(Weight.HEAVY, BigDecimal("79.3")))
        val adultFemaleSuperHeavy = adultFemaleRooster.setWeight(Weight(Weight.SUPER_HEAVY, BigDecimal("300")))


        return listOf(adultMaleRooster.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleRooster.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleRooster.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleLightFeather.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleLightFeather.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleLightFeather.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleFeather.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleFeather.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleFeather.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleLight.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleLight.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleLight.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleMiddle.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleMiddle.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleMiddle.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleMiddleHeavy.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleMiddleHeavy.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleMiddleHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleHeavy.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleHeavy.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleSuperHeavy.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleSuperHeavy.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleSuperHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleUltraHeavy.setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleUltraHeavy.setBeltType(BeltType.BLUE).setAgeDivision(BjjAgeDivisions.MASTER_1),
                adultMaleUltraHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(BjjAgeDivisions.MASTER_1).setFightDuration(BigDecimal(6)),

                adultFemaleRooster,
                adultFemaleRooster.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultFemaleLightFeather,
                adultFemaleLightFeather.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultFemaleFeather,
                adultFemaleFeather.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultFemaleLight,
                adultFemaleLight.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultFemaleMiddle,
                adultFemaleMiddle.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultFemaleMiddleHeavy,
                adultFemaleMiddleHeavy.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultFemaleHeavy,
                adultFemaleHeavy.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultFemaleSuperHeavy,
                adultFemaleSuperHeavy.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),

                adultMaleRooster,
                adultMaleRooster.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleRooster.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleRooster.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleRooster.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleLightFeather,
                adultMaleLightFeather.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleLightFeather.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleLightFeather.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleLightFeather.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleFeather,
                adultMaleFeather.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleFeather.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleFeather.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleFeather.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleLight,
                adultMaleLight.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleLight.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleLight.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleLight.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleMiddle,
                adultMaleMiddle.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleMiddle.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleMiddle.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleMiddle.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleMiddleHeavy,
                adultMaleMiddleHeavy.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleMiddleHeavy.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleMiddleHeavy.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleMiddleHeavy.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleHeavy,
                adultMaleHeavy.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleHeavy.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleHeavy.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleHeavy.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleSuperHeavy,
                adultMaleSuperHeavy.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleSuperHeavy.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleSuperHeavy.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleSuperHeavy.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10)),

                adultMaleUltraHeavy,
                adultMaleUltraHeavy.setBeltType(BeltType.BLUE).setFightDuration(BigDecimal(6)),
                adultMaleUltraHeavy.setBeltType(BeltType.PURPLE).setFightDuration(BigDecimal(7)),
                adultMaleUltraHeavy.setBeltType(BeltType.BROWN).setFightDuration(BigDecimal(8)),
                adultMaleUltraHeavy.setBeltType(BeltType.BLACK).setFightDuration(BigDecimal(10))
        )
    }
}