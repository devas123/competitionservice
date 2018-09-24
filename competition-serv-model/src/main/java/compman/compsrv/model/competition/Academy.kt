package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor


data class Academy @JsonCreator @PersistenceConstructor constructor(@JsonProperty("name") val name: String,
                                                                    @JsonProperty("coaches") private val coaches: List<String>?,
                                                                    @JsonProperty("created") val created: Long?) {
    constructor(name: String, coaches: List<String>) : this(name, coaches, System.currentTimeMillis())
    @Id @JsonProperty("id") val id: String = name.toLowerCase().replace(" ", "")
    fun addCoach(coach: String) = copy(coaches = if (coaches == null) listOf(coach) else coaches + coach)
}