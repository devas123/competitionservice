package compman.compsrv.cluster

import java.io.Serializable

data class MemberMetadata(val restPort: String?, val memberHostName: String?) : Serializable
