package compman.compsrv.query.model


import compservice.model.protobuf.model.CompetitorRegistrationStatus

import java.time.Instant

case class CompetitorDisplayInfo(
  competitorId: String,
  competitorFirstName: Option[String],
  competitorLastName: Option[String],
  competitorAcademyName: Option[String]
)

case class Competitor(
  competitionId: String,
  userId: Option[String],
  email: String,
  id: String,
  firstName: String,
  lastName: String,
  birthDate: Option[Instant],
  academy: Option[Academy],
  categories: Set[String],
  isPlaceholder: Boolean,
  promo: Option[String],
  registrationStatus: Option[CompetitorRegistrationStatus]
)

case class Academy(academyId: String, academyName: String)
