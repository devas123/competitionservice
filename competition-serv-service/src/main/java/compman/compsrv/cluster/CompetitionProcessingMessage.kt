package compman.compsrv.cluster

data class CompetitionProcessingMessage(val memberWithRestPort: MemberWithRestPort, val info: CompetitionProcessingInfo)