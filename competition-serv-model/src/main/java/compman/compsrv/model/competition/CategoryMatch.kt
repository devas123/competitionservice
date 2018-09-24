package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor

data class CategoryMatch
@JsonCreator
@PersistenceConstructor
constructor(@Id @JsonProperty("id") val categoryId: String, val fightsByRounds: Map<Int, Array<FightDescription>>)