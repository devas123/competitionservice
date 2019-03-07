package compman.compsrv.cluster

import compman.compsrv.model.dto.competition.CompetitionStateSnapshot
import io.scalecube.cluster.Member

data class CompetitionStateSnapshotMessage(val member: Member, val snapshot: CompetitionStateSnapshot)