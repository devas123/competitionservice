package compman.compsrv.query.model

import java.time.Instant

case class RegistrationPeriod(
  competitionId: String,
  id: String,
  name: Option[String],
  start: Option[Instant],
  end: Option[Instant],
  registrationGroupIds: Set[String]
)
