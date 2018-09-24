package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.*

//@CompoundIndexes(CompoundIndex(name = "ageGender", unique = false, def = "{ageDivision._id: 1, gender._id: 1}"))
@JsonIgnoreProperties(ignoreUnknown = true)
data class Category
@JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("ageDivision") val ageDivision: AgeDivision?,
            @JsonProperty("gender") val gender: String?,
            @JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("categoryId") val categoryId: String?,
            @JsonProperty("weight") val weight: Weight?,
            @JsonProperty("beltType") val beltType: String?,
            @JsonProperty("fightDuration") val fightDuration: BigDecimal) {
    fun createId() = competitionId + "_" + String(Base64.getEncoder().encode("${ageDivision?.name}/$gender/${weight?.id}/$beltType".toByteArray(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)

    fun setCategoryId(categoryId: String) = copy(categoryId = categoryId)
    fun setCompetitionId(competitionId: String) = copy(competitionId = competitionId)
    fun setWeight(w: Weight) = copy(weight = w)
    fun setGender(g: String) = copy(gender = g)
    fun setAgeDivision(ageDivision: AgeDivision) = copy(ageDivision = ageDivision)
    fun setBeltType(b: String) = copy(beltType = b)
    fun setFightDuration(d: BigDecimal) = copy(fightDuration = d)
    fun withNewCategoryId() = copy(categoryId = createId())
}