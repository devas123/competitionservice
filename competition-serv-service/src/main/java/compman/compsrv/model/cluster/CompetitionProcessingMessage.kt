package compman.compsrv.model.cluster

import io.scalecube.cluster.Member

data class CompetitionProcessingMessage(val member: Member, val info: CompetitionProcessingInfo)