package compman.compsrv.model.es.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor

data class EventHolder @JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("categoryId") val categoryId: String?,
            @JsonProperty("matId") val matId: String?,
            @JsonProperty("type") val type: EventType,
            @JsonProperty("eventOffset") val eventOffset: Long,
            @JsonProperty("eventPartition") val eventPartition: Int,
            @JsonProperty("commandOffset") val commandOffset: Long,
            @JsonProperty("commandPartition") val commandPartition: Int,
            @JsonProperty("payload") var payload: Map<String, Any?>?,
            @JsonProperty("metadata") val metadata: Map<String, Any>?) {
    @JsonProperty("timestamp")
    val timestamp: Long = System.currentTimeMillis()

    constructor(competitionId: String, categoryId: String?, matId: String?, type: EventType, payload: Map<String, Any?>?) : this(competitionId, categoryId, matId, type, -1L, -1, -1L, -1, payload, emptyMap())

    fun setCommandOffset(commandOffset: Long) = copy(commandOffset = commandOffset)
    fun setCommandPartition(commandPartition: Int) = copy(commandPartition = commandPartition)
    fun setEventOffset(eventOffset: Long) = copy(eventOffset = eventOffset)
    fun setEventPartition(eventPartition: Int) = copy(eventPartition = eventPartition)
    fun setCategoryId(categoryId: String?) = copy(categoryId = categoryId)
    fun setCompetitionId(competitionId: String) = copy(competitionId = competitionId)
    fun setMatId(matId: String) = copy(matId = matId)
    fun setMetadata(metadata: Map<String, Any>) = copy(metadata = metadata)
}