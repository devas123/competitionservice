package compman.compsrv.jpa.es.commands

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import compman.compsrv.jpa.es.events.MetadataEntry
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import java.io.Serializable
import java.util.*
import javax.persistence.*
import kotlin.collections.LinkedHashMap

@Entity
data class
Command(@Id
        val id: String?,
        val correlationId: String?,
        val competitionId: String,
        val type: CommandType,
        val categoryId: String?,
        val matId: String?,
        @JsonDeserialize(`as` = LinkedHashMap::class)
        @Lob
        val payload: Serializable?,
        @ElementCollection
        @CollectionTable(
                name = "COMMAND_METADATA_ENTRY",
                joinColumns = [JoinColumn(name = "COMMAND_METADATA_ID")]
        )
        val metadata: List<MetadataEntry>?,
        val executed: Boolean = false) {

    companion object {
        fun fromDTO(dto: CommandDTO) = Command(dto.id, dto.correlationId, dto.competitionId, dto.type, dto.categoryId, dto.matId, dto.payload, MetadataEntry.fromMap(dto.metadata), dto.executed)
    }

    @Column
    val timestamp: Long = System.currentTimeMillis()

    constructor(correlationId: String, competitionId: String, type: CommandType, categoryId: String?, payload: Serializable?) : this(UUID.randomUUID().toString(), correlationId, competitionId, type, categoryId, null, payload, emptyList())
    constructor(competitionId: String, type: CommandType, categoryId: String?, payload: Serializable?) : this(UUID.randomUUID().toString(), UUID.randomUUID().toString(), competitionId, type, categoryId, null, payload, emptyList())
    constructor(correlationId: String, competitionId: String, type: CommandType, categoryId: String?, matId: String?, payload: Serializable?) : this(UUID.randomUUID().toString(), correlationId, competitionId, type, categoryId, matId, payload, emptyList())
    constructor(competitionId: String, type: CommandType, categoryId: String?, matId: String?, payload: Serializable?) : this(UUID.randomUUID().toString(), UUID.randomUUID().toString(), competitionId, type, categoryId, matId, payload, emptyList())

    fun setCategoryId(categoryId: String) = copy(categoryId = categoryId)
    fun setMatId(matId: String) = copy(matId = matId)

    override fun toString(): String {
        return "Command(competitionId='$competitionId', type=$type, categoryId=$categoryId, matId=$matId, payload=$payload, timestamp=$timestamp)"
    }

    fun setMetadata(metadata: Map<String, String>) = copy(metadata = MetadataEntry.fromMap(metadata))
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Command

        if (correlationId != other.correlationId) return false

        return true
    }

    override fun hashCode(): Int {
        return correlationId.hashCode()
    }
}