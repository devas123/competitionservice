package compman.compsrv.jpa.cluster

import compman.compsrv.cluster.MemberWithRestPort

data class CompetitionProcessingMessage(val memberWithRestPort: MemberWithRestPort, val info: CompetitionProcessingInfo)