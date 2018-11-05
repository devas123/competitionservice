package compman.compsrv.model.competition

import com.compmanager.model.payment.RegistrationStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class Competitor
@PersistenceConstructor
@JsonCreator
constructor(@JsonProperty("id") val id: String,
            @JsonProperty("userId") val userId: String?,
            @JsonProperty("firstName") val firstName: String,
            @JsonProperty("lastName") val lastName: String,
            @JsonProperty("birthDate") val birthDate: Date?,
            @JsonProperty("academy") val academy: String?,
            @JsonProperty("category") val category: Category,
            @JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("registrationStatus") val registrationStatus: RegistrationStatus?,
            @JsonProperty("promo") val promo: String?) {
    val timestamp: LocalDateTime = LocalDateTime.now()
    fun setRegistrationStatus(registrationStatus: RegistrationStatus) = copy(registrationStatus = registrationStatus)

    override fun equals(other: Any?) = if (other is Competitor) {
        id == other.id
    } else {
        false
    }

    override fun hashCode() = id.hashCode()
}