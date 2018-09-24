package compman.compsrv.model.schedule

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.util.*

data class DashboardPeriod @PersistenceConstructor @JsonCreator
constructor(@JsonProperty("id") val id: String,
            @JsonProperty("name") val name: String,
            @JsonProperty("matIds") val matIds: Array<String>,
            @JsonProperty("startTime") val startTime: Date,
            @JsonProperty("isActive") val isActive: Boolean) {

    constructor(id: String, name: String) : this(id, name, emptyArray(), Date(), false)

    override fun toString(): String {
        return "DashboardPeriod(id='$id', name='$name', matIds='$matIds', startTime=$startTime, isActive=$isActive)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DashboardPeriod

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun setActive(active: Boolean) = copy(isActive = active)
    fun addMat(s: String) = copy(matIds = matIds + s)
}