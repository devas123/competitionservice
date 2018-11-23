package compman.compsrv.model.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.schedule.Schedule
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal
import java.util.*

data class CompetitionPropertiesDTO @PersistenceConstructor @JsonCreator
constructor(
        @JsonProperty("competitionId") val competitionId: String,
        @JsonProperty("creatorId") val creatorId: String,
        @JsonProperty("competitionName") val competitionName: String,
        @JsonProperty("registrationFee") val registrationFee: BigDecimal?,
        @JsonProperty("startDate") val startDate: Date?,
        @JsonProperty("schedulePublished") val schedulePublished: Boolean,
        @JsonProperty("bracketsPublished") val bracketsPublished: Boolean,
        @JsonProperty("schedule") val schedule: Schedule?,
        @JsonProperty("status") val status: CompetitionStatus,
        @JsonProperty("endDate") val endDate: Date?,
        @JsonProperty("registrationOpen") val registrationOpen: Boolean?) {

    constructor(props: CompetitionState) : this(props.competitionId,
            props.properties.creatorId,
            props.properties.competitionName,
            null,
            Date.from(props.properties.startDate?.toInstant()),
            props.properties.schedulePublished,
            props.properties.bracketsPublished,
            props.schedule,
            props.status,
            Date.from(props.properties.endDate?.toInstant()),
            null)
}