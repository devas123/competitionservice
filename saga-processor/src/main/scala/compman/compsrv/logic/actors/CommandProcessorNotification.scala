package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.NotificationTypes.NotificationType

sealed trait CommandProcessorNotification {
  val notificationType: NotificationType
  val competitionId: Option[String]
}

final case class CompetitionProcessingStarted(id: String) extends CommandProcessorNotification {
  override val notificationType: NotificationType = NotificationTypes.CompetitionProcessingStarted
  override val competitionId: Option[String] = Some(id)
}

final case class CompetitionProcessingStopped(id: String) extends CommandProcessorNotification {
  override val notificationType: NotificationType = NotificationTypes.CompetitionProcessingStopped
  override val competitionId: Option[String] = Some(id)
}
