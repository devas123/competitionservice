package compman.compsrv.model

import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CompetitorDTO}

object Errors {
  sealed trait Error
  final case class InternalError(msg: Option[String] = None) extends Error
  final case class NoPayloadError() extends Error
  final case class NoCategoryIdError() extends Error
  final case class CompetitorAlreadyExists(id: String, competitor: CompetitorDTO) extends Error
  final case class CompetitorDoesNotExist(id: String) extends Error
  final case class CategoryAlreadyExists(id: String, category: CategoryDescriptorDTO) extends Error
  final case class CategoryDoesNotExist(ids: Array[String]) extends Error
  final case class StageDoesNotExist(id: String) extends Error
  final case class FightDoesNotExist(id: String) extends Error
  final case class FightCannotBeMoved(id: String) extends Error
  final case class MatDoesNotExist(id: String) extends Error
  final case class RegistrationGroupDoesNotExist(id: String) extends Error
  final case class RegistrationPeriodDoesNotExist(id: String) extends Error
}
