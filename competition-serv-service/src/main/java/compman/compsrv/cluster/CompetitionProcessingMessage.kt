package compman.compsrv.cluster

import java.io.Serializable

data class CompetitionProcessingMessage(val correlationId: String?, val memberWithRestPort: MemberWithRestPort, val info: CompetitionProcessingInfo): Serializable {
    constructor(memberWithRestPort: MemberWithRestPort, info: CompetitionProcessingInfo): this(null, memberWithRestPort, info)
}