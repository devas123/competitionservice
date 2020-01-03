package compman.compsrv.service

import compman.compsrv.jpa.competition.BeltType
import compman.compsrv.model.dto.competition.AgeDivisionDTO
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.Gender
import compman.compsrv.model.dto.competition.WeightDTO
import compman.compsrv.util.IDGenerator
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class CategoryGeneratorService {
    fun createDefaultBjjCategories(competitionId: String): List<CategoryDescriptorDTO> {
        val adultMaleRooster = CategoryDescriptorDTO("BJJ", AgeDivisionDTO.ADULT, Gender.MALE.name, WeightDTO(IDGenerator.hashString(WeightDTO.ROOSTER), BigDecimal("57.5")).setName(WeightDTO.ROOSTER), BeltType.WHITE, UUID.randomUUID().toString(), BigDecimal(5), emptyArray())
        val adultMaleLightFeather = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.LIGHT_FEATHER), BigDecimal("64")).setName(WeightDTO.LIGHT_FEATHER))
        val adultMaleFeather = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.FEATHER), BigDecimal("70")).setName(WeightDTO.FEATHER))
        val adultMaleLight = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.LIGHT), BigDecimal("76")).setName(WeightDTO.LIGHT))
        val adultMaleMiddle = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.MIDDLE), BigDecimal("82.3")).setName(WeightDTO.MIDDLE))
        val adultMaleMiddleHeavy = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.MEDIUM_HEAVY), BigDecimal("88.3")).setName(WeightDTO.MEDIUM_HEAVY))
        val adultMaleHeavy = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.HEAVY), BigDecimal("94.3")).setName(WeightDTO.HEAVY))
        val adultMaleSuperHeavy = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.SUPER_HEAVY), BigDecimal("100.5")).setName(WeightDTO.SUPER_HEAVY))
        val adultMaleUltraHeavy = adultMaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.ULTRA_HEAVY), BigDecimal("300")).setName(WeightDTO.ULTRA_HEAVY))

        val adultFemaleRooster = CategoryDescriptorDTO("BJJ", AgeDivisionDTO.ADULT, Gender.FEMALE.name, WeightDTO(IDGenerator.hashString(WeightDTO.ROOSTER), BigDecimal("48.5")).setName(WeightDTO.ROOSTER), BeltType.WHITE, UUID.randomUUID().toString(), BigDecimal(5), emptyArray())
        val adultFemaleLightFeather = adultFemaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.LIGHT_FEATHER), BigDecimal("53.5")).setName(WeightDTO.LIGHT_FEATHER))
        val adultFemaleFeather = adultFemaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.FEATHER), BigDecimal("58.5")).setName(WeightDTO.FEATHER))
        val adultFemaleLight = adultFemaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.LIGHT), BigDecimal("64")).setName(WeightDTO.LIGHT))
        val adultFemaleMiddle = adultFemaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.MIDDLE), BigDecimal("69")).setName(WeightDTO.MIDDLE))
        val adultFemaleMiddleHeavy = adultFemaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.MEDIUM_HEAVY), BigDecimal("74")).setName(WeightDTO.MEDIUM_HEAVY))
        val adultFemaleHeavy = adultFemaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.HEAVY), BigDecimal("79.3")).setName(WeightDTO.HEAVY))
        val adultFemaleSuperHeavy = adultFemaleRooster.setWeight(WeightDTO(IDGenerator.hashString(WeightDTO.SUPER_HEAVY), BigDecimal("300")).setName(WeightDTO.SUPER_HEAVY))


        return listOf(adultMaleRooster.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleRooster.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleRooster.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleLightFeather.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleLightFeather.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleLightFeather.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleFeather.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleFeather.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleFeather.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleLight.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleLight.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleLight.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleMiddle.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleMiddle.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleMiddle.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleMiddleHeavy.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleMiddleHeavy.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleMiddleHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleHeavy.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleHeavy.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleSuperHeavy.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleSuperHeavy.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleSuperHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

                adultMaleUltraHeavy.setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleUltraHeavy.setBeltType(BeltType.BLUE).setAgeDivision(AgeDivisionDTO.MASTER_1),
                adultMaleUltraHeavy.setBeltType(BeltType.PURPLE).setAgeDivision(AgeDivisionDTO.MASTER_1).setFightDuration(BigDecimal(6)),

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