package compman.compsrv.model.es.commands

import compman.compsrv.model.es.events.MetadataEntry
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity

@Entity
data class Command(
        val correlationId: String,
        val competitionId: String,
        val type: CommandType,
        val categoryId: String?,
        val matId: String?,
        val payload: ByteArray?,
        val metadata: List<MetadataEntry>?,
        val executed: Boolean = false) {

    @Column
    val timestamp: Long = System.currentTimeMillis()

    constructor(correlationId: String, competitionId: String, type: CommandType, categoryId: String?, payload: ByteArray?) : this(correlationId, competitionId, type, categoryId, null, payload, emptyList())
    constructor(competitionId: String, type: CommandType, categoryId: String?, payload: ByteArray?) : this(UUID.randomUUID().toString(), competitionId, type, categoryId, null, payload, emptyList())
    constructor(correlationId: String, competitionId: String, type: CommandType, categoryId: String?, matId: String?, payload: ByteArray?) : this(correlationId, competitionId, type, categoryId, matId, payload, emptyList())
    constructor(competitionId: String, type: CommandType, categoryId: String?, matId: String?, payload: ByteArray?) : this(UUID.randomUUID().toString(), competitionId, type, categoryId, matId, payload, emptyList())

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