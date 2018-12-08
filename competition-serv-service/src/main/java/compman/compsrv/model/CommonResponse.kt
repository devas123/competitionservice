package compman.compsrv.model

data class CommonResponse(val status: Int, val statusText: String, val payload: ByteArray?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommonResponse

        if (status != other.status) return false
        if (statusText != other.statusText) return false
        if (payload != null) {
            if (other.payload == null) return false
            if (!payload.contentEquals(other.payload)) return false
        } else if (other.payload != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + statusText.hashCode()
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        return result
    }
}
