package compman.compsrv.query.model

import compman.compsrv.model.dto.competition.CompetitionStatus

import java.time.Instant

case class ManagedCompetition(
  id: String,
  competitionName: String,
  eventsTopic: String,
  creatorId: String,
  createdAt: Instant,
  startsAt: Instant,
  endsAt: Option[Instant],
  timeZone: String,
  status: CompetitionStatus
)
