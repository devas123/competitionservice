package compman.compsrv.jpa.competition

import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.CompetitorDTO
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity


@Entity
class Competitor(
        id: String,
        var email: String,
        var userId: String?,
        var firstName: String,
        var lastName: String,
        var birthDate: Instant?,
        var academy: String?,
        @Column(name = "category_id",
                columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
        var categoryId: String,
        @Column(name = "competition_id",
                columnDefinition = "VARCHAR(255) REFERENCES competition_properties(id)")
        var competitionId: String,
        var registrationStatus: RegistrationStatus?,
        var promo: String?) : AbstractJpaPersistable<String>(id) {

    fun toDTO(): CompetitorDTO {
        return CompetitorDTO()
                .setId(id)
                .setAcademy(academy)
                .setBirthDate(birthDate)
                .setCategoryId(categoryId)
                .setCompetitionId(competitionId)
                .setEmail(email)
                .setFirstName(firstName)
                .setLastName(lastName)
                .setPromo(promo)
                .setRegistrationStatus(registrationStatus?.name)
                .setUserId(userId)
    }

    companion object {
        fun fromDTO(dto: CompetitorDTO) =
                Competitor(
                        id = dto.id,
                        email = dto.email,
                        userId = dto.userId,
                        firstName = dto.firstName,
                        lastName = dto.lastName,
                        birthDate = dto.birthDate,
                        academy = dto.academy,
                        categoryId = dto.categoryId,
                        competitionId = dto.competitionId,
                        registrationStatus = RegistrationStatus.valueOf(dto.registrationStatus),
                        promo = dto.promo
                )
    }
}