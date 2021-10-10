package compman.compsrv.model

import compman.compsrv.model.NotificationTypes.NotificationType
import compman.compsrv.model.dto.competition.CompetitionStatus

import java.time.Instant

sealed trait CommandProcessorNotification {
  val notificationType: NotificationType
}

final case class CompetitionProcessingStarted(
  id: String,
  topic: String,
  creatorId: String,
  createdAt: Instant,
  startsAt: Instant,
  endsAt: Instant,
  timeZone: String,
  status: CompetitionStatus
) extends CommandProcessorNotification {
  override val notificationType: NotificationType = NotificationTypes.ProcessingStarted
}

final case class CompetitionProcessingStopped(id: String) extends CommandProcessorNotification {
  override val notificationType: NotificationType = NotificationTypes.ProcessingStopped
}
