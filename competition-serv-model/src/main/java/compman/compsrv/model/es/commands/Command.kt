package compman.compsrv.model.es.commands

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class Command @JsonCreator
@PersistenceConstructor
constructor(
        @JsonProperty("correlationId") val correlatioId: String?,
        @JsonProperty("competitionId") val competitionId: String,
        @JsonProperty("type") val type: CommandType,
        @JsonProperty("categoryId") val categoryId: String?,
        @JsonProperty("matId") val matId: String?,
        @JsonProperty("payload") val payload: Map<String, Any?>?,
        @JsonProperty("metadata") val metadata: Map<String, Any>?) {
    @JsonProperty("timestamp")
    val timestamp: Long = System.currentTimeMillis()

    constructor(correlationId: String, competitionId: String, type: CommandType, categoryId: String?, payload: Map<String, Any?>?) : this(correlationId, competitionId, type, categoryId, null, payload, emptyMap())
    constructor(competitionId: String, type: CommandType, categoryId: String?, payload: Map<String, Any?>?) : this(UUID.randomUUID().toString(), competitionId, type, categoryId, null, payload, emptyMap())
    constructor(correlationId: String, competitionId: String, type: CommandType, categoryId: String?, matId: String?, payload: Map<String, Any?>?) : this(correlationId, competitionId, type, categoryId, matId, payload, emptyMap())
    constructor(competitionId: String, type: CommandType, categoryId: String?, matId: String?, payload: Map<String, Any?>?) : this(UUID.randomUUID().toString(), competitionId, type, categoryId, matId, payload, emptyMap())


    fun setCategoryId(categoryId: String) = copy(categoryId = categoryId)
    fun setMatId(matId: String) = copy(matId = matId)


    override fun toString(): String {
        return "Command(competitionId='$competitionId', type=$type, categoryId=$categoryId, matId=$matId, payload=$payload, timestamp=$timestamp)"
    }

    fun setMetadata(metadata: Map<String, String>) = copy(metadata = metadata)
}