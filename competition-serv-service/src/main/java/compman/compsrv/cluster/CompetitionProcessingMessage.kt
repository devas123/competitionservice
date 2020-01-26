package compman.compsrv.cluster

import java.io.Serializable

data class CompetitionProcessingMessage(val correlationId: String?, val memberWithRestPort: MemberWithRestPort, val info: CompetitionProcessingInfo): Serializable