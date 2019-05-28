package compman.compsrv.cluster

import java.io.Serializable

data class CompetitionProcessingMessage(val memberWithRestPort: MemberWithRestPort, val info: CompetitionProcessingInfo): Serializable