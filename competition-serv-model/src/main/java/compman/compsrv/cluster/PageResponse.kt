package compman.compsrv.cluster

import java.util.*

data class PageResponse<T>(val competitionId: String, val total: Long, val page: Int, val data: Array<T>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageResponse<*>

        if (total != other.total) return false
        if (page != other.page) return false
        if (!Arrays.equals(data, other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = total
        result = 31 * result + page
        result = 31 * result + Arrays.hashCode(data)
        return result.toInt()
    }
}
