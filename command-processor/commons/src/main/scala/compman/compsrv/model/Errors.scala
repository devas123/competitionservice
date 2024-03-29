package compman.compsrv.model

import compservice.model.protobuf.model.{CategoryDescriptor, Competitor, FightResultOption, StageDescriptor}

object Errors {
  sealed trait Error
  final case class InternalError(msg: Option[String] = None) extends Error
  final case class InternalException(ex: Throwable) extends Error
  object InternalError {
    def apply(msg: Option[String] = None) = new InternalError(msg)
    def apply(msg: String) = new InternalError(Option(msg))
  }

  final case class NoPayloadError() extends Error
  final case class FightResultOptionsMissing(stageId: String) extends Error
  final case class DrawResultsOnlyAllowedInGroups(stageId: String, fightResults: List[FightResultOption]) extends Error
  final case class InputDescriptorInvalidForStage(stages: Seq[StageDescriptor]) extends Error
  final case class FinalStageIsNotUniqueOrMissing(stages: Seq[StageDescriptor]) extends Error
  final case class NoScheduleError() extends Error
  final case class NoRegistrationInfoError() extends Error
  final case class NoCompetitionPropertiesError() extends Error
  final case class TimeoutError() extends Error
  final case class InvalidPayload(payload: Any) extends Error
  final case class NotAllSchedulePeriodsHaveIds() extends Error
  final case class RegistrationPeriodAlreadyExistsError(id: String) extends Error
  final case class RegistrationGroupAlreadyExistsError(id: Set[String]) extends Error
  final case class RegistrationGroupDefaultAlreadyExistsError() extends Error
  final case class NoCategoryIdError() extends Error
  final case class NoCompetitionIdError() extends Error
  final case class NoStageDigraphError() extends Error
  final case class NoCommandIdError() extends Error
  final case class CompetitorAlreadyExists(id: String, competitor: Competitor) extends Error
  final case class CompetitorDoesNotExist(id: String) extends Error
  final case class CategoryAlreadyExists(id: String, category: CategoryDescriptor) extends Error
  final case class CategoryListIsEmpty() extends Error
  final case class CategoryDoesNotExist(ids: Seq[String]) extends Error
  final case class CategoryIsNotEmptyError() extends Error
  final case class BracketsAlreadyGeneratedForCategory(categoryId: String) extends Error
  final case class StageDoesNotExist(id: String) extends Error
  final case class StageGraphMissing() extends Error
  final case class StageResultDescriptorMissing() extends Error
  final case class StageResultsMissing() extends Error
  final case class FightDoesNotExist(id: String) extends Error
  final case class FightCannotBeMoved(id: String) extends Error
  final case class MatDoesNotExist(id: String) extends Error
  final case class RegistrationGroupDoesNotExist(id: String) extends Error
  final case class RegistrationPeriodDoesNotExist(id: String) extends Error
}
