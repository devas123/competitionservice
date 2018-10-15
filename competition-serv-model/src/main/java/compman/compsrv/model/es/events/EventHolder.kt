package compman.compsrv.model.es.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor

data class EventHolder @JsonCreator
@PersistenceConstructor
constructor(
        @JsonProperty("correlationId") val correlationId: String,
        @JsonProperty("competitionId") val competitionId: String,
        @JsonProperty("categoryId") val categoryId: String?,
        @JsonProperty("matId") val matId: String?,
        @JsonProperty("type") val type: EventType,
        @JsonProperty("payload") var payload: Map<String, Any?>?,
        @JsonProperty("metadata") val metadata: Map<String, Any>?) {
    @JsonProperty("timestamp")
    val timestamp: Long = System.currentTimeMillis()

    constructor(correlationId: String, competitionId: String, categoryId: String?, matId: String?, type: EventType, payload: Map<String, Any?>?) : this(correlationId, competitionId, categoryId, matId, type, payload, emptyMap())

    fun setCategoryId(categoryId: String?) = copy(categoryId = categoryId)
    fun setCompetitionId(competitionId: String) = copy(competitionId = competitionId)
    fun setMatId(matId: String) = copy(matId = matId)
    fun setMetadata(metadata: Map<String, Any>) = copy(metadata = metadata)
}