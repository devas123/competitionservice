package compman.compsrv.model

import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CompetitorDTO}

object Errors {
  sealed trait Error
  final case class InternalError() extends Error
  final case class NoPayloadError() extends Error
  final case class CompetitorAlreadyExists(id: String, competitor: CompetitorDTO) extends Error
  final case class CategoryAlreadyExists(id: String, category: CategoryDescriptorDTO) extends Error
  final case class CategoryDoesNotExist(ids: Array[String]) extends Error
  final case class RegistrationGroupDoesNotExist(id: String) extends Error
  final case class RegistrationPeriodDoesNotExist(id: String) extends Error
}
