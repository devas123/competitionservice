package compman.compsrv.query.model


import compservice.model.protobuf.model.CompetitionStatus

import java.time.Instant

case class ManagedCompetition(
                               id: String,
                               competitionName: Option[String],
                               eventsTopic: String,
                               creatorId: Option[String],
                               creationTimestamp: Instant,
                               startDate: Instant,
                               endDate: Option[Instant],
                               timeZone: String,
                               status: CompetitionStatus
)
