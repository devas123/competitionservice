package compman.compsrv.jpa.es.events

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import java.util.*
import javax.persistence.*

@Embeddable
class MetadataEntry(
        @Column(name = "metadata_key")
        var key: String,
        @Column(name = "metadata_value")
        var value: String) {
    companion object {
        fun fromMap(metadata: Map<String, String>?): List<MetadataEntry> =
                metadata?.map { MetadataEntry(it.key, it.value) } ?: emptyList()

        fun toMap(metadataList: List<MetadataEntry>?): Map<String, String> = metadataList?.map { it.key to it.value }?.toMap()
                ?: emptyMap()
    }
}

@Entity
class EventHolder(id: String?,
                  @Column(name = "correlation_id",
                          columnDefinition = "VARCHAR(255)")
                  var correlationId: String?,
                  @Column(name = "competition_id",
                          columnDefinition = "VARCHAR(255) REFERENCES competition_state(id)")
                  var competitionId: String,
                  @Column(name = "category_id", nullable = true,
                          columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
                  var categoryId: String?,
                  var matId: String?,
                  var type: EventType,
                  @Lob
                  var payload: String?,

                  @ElementCollection
                  @CollectionTable(
                          name = "EVENT_METADATA_ENTRY",
                          joinColumns = [JoinColumn(name = "EVENT_METADATA_ID")]
                  )
                  var metadata: List<MetadataEntry>?) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: EventDTO) = EventHolder(dto.id ?: UUID.randomUUID().toString(), dto.correlationId, dto.competitionId, dto.categoryId, dto.matId, dto.type, dto.payload, MetadataEntry.fromMap(dto.metadata))
    }

    @Version
    val timestamp: Long = System.currentTimeMillis()

    fun findMetadataByKey(key: String): String? = metadata?.find { it.key == key }?.value

    constructor(correlationId: String?, competitionId: String, categoryId: String?, matId: String?, type: EventType, payload: String?) : this(UUID.randomUUID().toString(), correlationId, competitionId, categoryId, matId, type, payload, emptyList())
}