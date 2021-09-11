package compman.compsrv.model

import compman.compsrv.model.NotificationTypes.NotificationType

sealed trait CommandProcessorNotification {
  val notificationType: NotificationType
  val competitionId: Option[String]
  val eventTopic: Option[String] = None
}

final case class CompetitionProcessingStarted(id: String, topic: String) extends CommandProcessorNotification {
  override val notificationType: NotificationType = NotificationTypes.ProcessingStarted
  override val competitionId: Option[String] = Some(id)
  override val eventTopic: Option[String] = Some(topic)
}

final case class CompetitionProcessingStopped(id: String) extends CommandProcessorNotification {
  override val notificationType: NotificationType = NotificationTypes.ProcessingStopped
  override val competitionId: Option[String] = Some(id)
}
