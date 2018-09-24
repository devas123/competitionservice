package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor

/**
 * Created by ezabavno on 24.03.2017.
 */
data class WeightsTable
@JsonCreator
@PersistenceConstructor
constructor(@Id @JsonProperty("males") val males: String, @JsonProperty("females") val category: Category?)