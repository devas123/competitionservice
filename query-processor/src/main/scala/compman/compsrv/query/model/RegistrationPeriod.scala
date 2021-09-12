package compman.compsrv.query.model

import java.time.Instant

case class RegistrationPeriod(
  competitionId: String,
  id: String,
  name: String,
  start: Instant,
  end: Instant,
  registrationGroupIds: Set[String]
)
