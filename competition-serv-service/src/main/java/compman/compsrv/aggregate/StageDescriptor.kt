package compman.compsrv.aggregate

import compman.compsrv.model.dto.brackets.StageDescriptorDTO

data class StageDescriptor(val id: String, val dto: StageDescriptorDTO, val fights: Set<String> = emptySet()) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StageDescriptor

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    val fightsMapIndices = fights.mapIndexed { index, it ->  it to index }.toMap()
}