package compman.compsrv.model.command

import compman.compsrv.model.Payload
import compman.compsrv.model.commands.payload._

object Commands {
  sealed trait Command[+P <: Payload] {
    def payload: Option[P]
    val competitionId: Option[String]
    val categoryId: Option[String]
    val competitorId: Option[String]
    val fightId: Option[String]
  }

  final case class AddCompetitorCommand(
      payload: Option[AddCompetitorPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[AddCompetitorPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AddCategory(
      payload: Option[AddCategoryPayload],
      competitionId: Option[String]
                                     ) extends Command[AddCategoryPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
    override val categoryId: Option[String]      = None
  }
  final case class AddRegistrationGroupCommand(
      payload: Option[AddRegistrationGroupPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[AddRegistrationGroupPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AddRegistrationPeriodCommand(
      payload: Option[AddRegistrationPeriodPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[AddRegistrationPeriodPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AssignRegistrationGroupCategoriesCommand(
      payload: Option[AssignRegistrationGroupCategoriesPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[AssignRegistrationGroupCategoriesPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryRegistrationStatusChangeCommand(
      payload: Option[CategoryRegistrationStatusChangePayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[CategoryRegistrationStatusChangePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class ChangeCompetitorCategoryCommand(
      payload: Option[ChangeCompetitorCategoryPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[ChangeCompetitorCategoryPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateCompetitionProperties(
      payload: Option[UpdateCompetionPropertiesPayload],
      competitionId: Option[String]
                                              ) extends Command[UpdateCompetionPropertiesPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
    override val categoryId: Option[String] = None
  }
  final case class ChangeFightOrderCommand(
      payload: Option[ChangeFightOrderPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[ChangeFightOrderPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CreateCompetitionCommand(
      payload: Option[CreateCompetitionPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[CreateCompetitionPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class DeleteRegistrationGroupCommand(
      payload: Option[DeleteRegistrationGroupPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[DeleteRegistrationGroupPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AddAcademyCommand(
      payload: Option[AddAcademyPayload],
  ) extends Command[AddAcademyPayload] {
    override val fightId: Option[String]      = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String] = None
    override val competitorId: Option[String] = None
  }
  final case class UpdateAcademyCommand(
      payload: Option[UpdateAcademyPayload],
  ) extends Command[UpdateAcademyPayload] {
    override val fightId: Option[String]      = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String] = None
    override val competitorId: Option[String] = None
  }
  final case class RemoveAcademyCommand(
      payload: Option[RemoveAcademyPayload],
  ) extends Command[RemoveAcademyPayload] {
    override val fightId: Option[String]      = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String] = None
    override val competitorId: Option[String] = None
  }
  final case class DeleteRegistrationPeriodCommand(
      payload: Option[DeleteRegistrationPeriodPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[DeleteRegistrationPeriodPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class FightEditorApplyChangesCommand(
      payload: Option[FightEditorApplyChangesPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[FightEditorApplyChangesPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class GenerateAbsoluteCategoryCommand(
      payload: Option[GenerateAbsoluteCategoryPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[GenerateAbsoluteCategoryPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class GenerateBracketsCommand(
      payload: Option[GenerateBracketsPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[GenerateBracketsPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  abstract class PayloadlessCommand() extends Command[Payload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
    override def payload: Option[Payload] = None
    override val categoryId: Option[String] = None
  }
  final case class DropScheduleCommand(competitionId: Option[String]) extends PayloadlessCommand
  final case class DropAllBracketsCommand(competitionId: Option[String])  extends PayloadlessCommand
  final case class StartCompetition(competitionId: Option[String])  extends PayloadlessCommand
  final case class StopCompetition(competitionId: Option[String])  extends PayloadlessCommand
  final case class PublishCompetition(competitionId: Option[String])  extends PayloadlessCommand
  final case class UnpublishCompetition(competitionId: Option[String])  extends PayloadlessCommand
  final case class DeleteCompetition(competitionId: Option[String])  extends PayloadlessCommand
  final case class DeleteCategoryCommand(competitionId: Option[String], override val categoryId: Option[String])  extends PayloadlessCommand
  final case class CreateFakeCompetitors(competitionId: Option[String], override val categoryId: Option[String])  extends PayloadlessCommand

  final case class DropBracketsCommand(competitionId: Option[String], override val categoryId: Option[String]) extends PayloadlessCommand
  final case class GenerateCategoriesFromRestrictionsCommand(
      payload: Option[GenerateCategoriesFromRestrictionsPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[GenerateCategoriesFromRestrictionsPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class GenerateScheduleCommand(
      payload: Option[GenerateSchedulePayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[GenerateSchedulePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class PropagateCompetitorsCommand(
      payload: Option[PropagateCompetitorsPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[PropagateCompetitorsPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class RegistrationPeriodAddRegistrationGroupCommand(
      payload: Option[RegistrationPeriodAddRegistrationGroupPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[RegistrationPeriodAddRegistrationGroupPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class RemoveCompetitorCommand(
      payload: Option[RemoveCompetitorPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[RemoveCompetitorPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class SetFightResultCommand(
      payload: Option[SetFightResultPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[SetFightResultPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateCompetitorCommand(
      payload: Option[UpdateCompetitorPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[UpdateCompetitorPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateRegistrationInfoCommand(
      payload: Option[UpdateRegistrationInfoPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[UpdateRegistrationInfoPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateStageStatusCommand(
      payload: Option[UpdateStageStatusPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends Command[UpdateStageStatusPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
}
