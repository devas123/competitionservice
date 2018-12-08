package compman.compsrv.model.cluster

import compman.compsrv.cluster.MemberWithRestPort

data class CompetitionProcessingMessage(val member: MemberWithRestPort, val info: CompetitionProcessingInfo)