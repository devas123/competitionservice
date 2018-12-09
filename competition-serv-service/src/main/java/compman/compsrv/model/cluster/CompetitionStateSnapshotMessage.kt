package compman.compsrv.model.cluster

import compman.compsrv.model.competition.CompetitionStateSnapshot
import io.scalecube.cluster.Member

data class CompetitionStateSnapshotMessage(val member: Member, val snapshot: CompetitionStateSnapshot)