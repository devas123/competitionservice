package compman.compsrv.aggregate

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import java.util.concurrent.atomic.AtomicLong

data class Competition(val id: String, val properties: CompetitionPropertiesDTO, val registrationInfo: RegistrationInfoDTO, val categories: Array<String> = emptyArray(),
                  val periods: Array<PeriodDTO> = emptyArray(), val mats: Array<MatDescriptionDTO> = emptyArray(), val competitionInfoTemplate: ByteArray = byteArrayOf()) : AbstractAggregate(AtomicLong(0), AtomicLong(0)) {


    fun groupExists(id: String) = registrationInfo.registrationGroups?.any { it.id == id } == true
    fun periodExists(id: String) = registrationInfo.registrationPeriods?.any { it.id == id } == true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Competition

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}