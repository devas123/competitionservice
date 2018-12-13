package compman.compsrv.model.cluster

import compman.compsrv.cluster.MemberWithRestPort

data class CompetitionProcessingMessage(val memberWithRestPort: MemberWithRestPort, val info: CompetitionProcessingInfo)