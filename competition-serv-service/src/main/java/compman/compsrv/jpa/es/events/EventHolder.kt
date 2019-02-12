package compman.compsrv.jpa.es.events

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import java.io.Serializable
import java.util.*
import javax.persistence.*

@Embeddable
data class MetadataEntry(
        @Column(name = "metadata_key")
        val key: String,
        @Column(name = "metadata_value")
        val value: String) {
    companion object {
        fun fromMap(metadata: Map<String, String>): List<MetadataEntry> =
                metadata.map { MetadataEntry(it.key, it.value) }

        fun toMap(metadataList: List<MetadataEntry>): Map<String, String> = metadataList.map { it.key to it.value }.toMap()
    }
}

@Entity
data class EventHolder(
        @Id
        val id: String?,
        @Column(name = "correlation_id",
                columnDefinition = "VARCHAR(255) REFERENCES competition_state(id)")
        val correlationId: String?,
        @Column(name = "competition_id",
                columnDefinition = "VARCHAR(255) REFERENCES competition_state(id)")
        val competitionId: String,
        @Column(name = "category_id", nullable = true,
                columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
        val categoryId: String?,
        val matId: String?,
        val type: EventType,
        @JsonDeserialize(`as` = LinkedHashMap::class)
        @Lob
        val payload: Serializable?,

        @ElementCollection
        @CollectionTable(
                name = "EVENT_METADATA_ENTRY",
                joinColumns = [JoinColumn(name = "EVENT_METADATA_ID")]
        )
        val metadata: List<MetadataEntry>?) {

    companion object {
        fun fromDTO(dto: EventDTO) = EventHolder(dto.id, dto.correlationId, dto.competitionId, dto.categoryId, dto.matId, dto.type, dto.payload, MetadataEntry.fromMap(dto.metadata))
    }

    @Version
    val timestamp: Long = System.currentTimeMillis()

    fun findMetadataByKey(key: String): String? = metadata?.find { it.key == key }?.value

    constructor(correlationId: String?, competitionId: String, categoryId: String?, matId: String?, type: EventType, payload: Serializable?) : this(UUID.randomUUID().toString(), correlationId, competitionId, categoryId, matId, type, payload, emptyList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventHolder

        if (correlationId != other.correlationId) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode() = 31
}