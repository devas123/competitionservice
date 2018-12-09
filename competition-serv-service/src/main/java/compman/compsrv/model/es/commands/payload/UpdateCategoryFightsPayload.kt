package compman.compsrv.model.es.commands.payload

import compman.compsrv.model.schedule.MatScheduleContainer

data class UpdateCategoryFightsPayload(val fightsByMats: Array<MatScheduleContainer>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UpdateCategoryFightsPayload

        if (!fightsByMats.contentEquals(other.fightsByMats)) return false

        return true
    }

    override fun hashCode(): Int {
        return fightsByMats.contentHashCode()
    }
}