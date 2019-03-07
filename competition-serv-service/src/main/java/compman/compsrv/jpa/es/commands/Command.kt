package compman.compsrv.jpa.es.commands

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.es.events.MetadataEntry
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import java.io.Serializable
import javax.persistence.*

@Entity
class
Command(id: String?,
        var correlationId: String?,
        var competitionId: String,
        var type: CommandType,
        var categoryId: String?,
        var matId: String?,
        @JsonDeserialize(`as` = LinkedHashMap::class)
        @Lob
        var payload: Serializable?,
        @ElementCollection
        @CollectionTable(
                name = "COMMAND_METADATA_ENTRY",
                joinColumns = [JoinColumn(name = "COMMAND_METADATA_ID")]
        )
        var metadata: List<MetadataEntry>?,
        var executed: Boolean = false) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: CommandDTO) = Command(dto.id
                ?: dto.correlationId, dto.correlationId, dto.competitionId, dto.type, dto.categoryId, dto.matId, dto.payload, MetadataEntry.fromMap(dto.metadata), dto.executed)
    }

    @Column
    var timestamp: Long = System.currentTimeMillis()

    override fun toString(): String {
        return "Command(competitionId='$competitionId', type=$type, categoryId=$categoryId, matId=$matId, payload=$payload, timestamp=$timestamp)"
    }

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