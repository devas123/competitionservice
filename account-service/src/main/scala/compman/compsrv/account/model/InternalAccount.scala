package compman.compsrv.account.model

import java.time.Instant

case class InternalAccount(
  userId: String,
  firstName: String,
  lastName: String,
  email: String,
  birthDate: Option[Instant],
  password: String
)
