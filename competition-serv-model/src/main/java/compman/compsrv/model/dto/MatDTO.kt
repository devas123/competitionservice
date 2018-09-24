package compman.compsrv.model.dto

import com.fasterxml.jackson.annotation.JsonCreator
import compman.compsrv.model.competition.FightDescription
import org.springframework.data.annotation.PersistenceConstructor

data class MatDTO @PersistenceConstructor @JsonCreator
constructor(val matId: String,
            val periodId: String,
            val numberOfFights: Int,
            val topFiveFights: Array<FightDescription>) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MatDTO

        if (matId != other.matId) return false
        if (periodId != other.periodId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = matId.hashCode()
        result = 31 * result + periodId.hashCode()
        return result
    }


}