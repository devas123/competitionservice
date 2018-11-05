package compman.compsrv.model.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.CompetitionProperties
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

    constructor(props: CompetitionProperties) : this(props.competitionId,
            props.creatorId,
            props.competitionName,
            props.registrationFee,
            props.startDate,
            props.schedulePublished,
            props.bracketsPublished,
            props.schedule,
            props.status,
            props.endDate,
            props.registrationOpen)
}