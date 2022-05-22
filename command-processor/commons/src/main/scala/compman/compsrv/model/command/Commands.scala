package compman.compsrv.model.command

import compman.compsrv.model.Errors
import compservice.model.protobuf.callback.{CommandCallback, CommandExecutionResult, ErrorCallback}
import compservice.model.protobuf.command.Command
import compservice.model.protobuf.commandpayload._
import compservice.model.protobuf.event.Event

object Commands {
  sealed trait InternalCommandProcessorCommand[+P] {
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
  ) extends InternalCommandProcessorCommand[AddCompetitorPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AddCategory(
      payload: Option[AddCategoryPayload],
      competitionId: Option[String]
                                     ) extends InternalCommandProcessorCommand[AddCategoryPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
    override val categoryId: Option[String]      = None
  }
  final case class AddRegistrationGroupCommand(
      payload: Option[AddRegistrationGroupPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[AddRegistrationGroupPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AddRegistrationPeriodCommand(
      payload: Option[AddRegistrationPeriodPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[AddRegistrationPeriodPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AssignRegistrationGroupCategoriesCommand(
      payload: Option[AssignRegistrationGroupCategoriesPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[AssignRegistrationGroupCategoriesPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryRegistrationStatusChangeCommand(
      payload: Option[CategoryRegistrationStatusChangePayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[CategoryRegistrationStatusChangePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class ChangeCompetitorCategoryCommand(
      payload: Option[ChangeCompetitorCategoryPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[ChangeCompetitorCategoryPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateCompetitionProperties(
      payload: Option[UpdateCompetionPropertiesPayload],
      competitionId: Option[String]
                                              ) extends InternalCommandProcessorCommand[UpdateCompetionPropertiesPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
    override val categoryId: Option[String] = None
  }
  final case class ChangeFightOrderCommand(
      payload: Option[ChangeFightOrderPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[ChangeFightOrderPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CreateCompetitionCommand(
      payload: Option[CreateCompetitionPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[CreateCompetitionPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class DeleteRegistrationGroupCommand(
      payload: Option[DeleteRegistrationGroupPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[DeleteRegistrationGroupPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class AddAcademyCommand(
      payload: Option[AddAcademyPayload],
  ) extends InternalCommandProcessorCommand[AddAcademyPayload] {
    override val fightId: Option[String]      = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String] = None
    override val competitorId: Option[String] = None
  }
  final case class UpdateAcademyCommand(
      payload: Option[UpdateAcademyPayload],
  ) extends InternalCommandProcessorCommand[UpdateAcademyPayload] {
    override val fightId: Option[String]      = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String] = None
    override val competitorId: Option[String] = None
  }
  final case class RemoveAcademyCommand(
      payload: Option[RemoveAcademyPayload],
  ) extends InternalCommandProcessorCommand[RemoveAcademyPayload] {
    override val fightId: Option[String]      = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String] = None
    override val competitorId: Option[String] = None
  }
  final case class DeleteRegistrationPeriodCommand(
      payload: Option[DeleteRegistrationPeriodPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[DeleteRegistrationPeriodPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class FightEditorApplyChangesCommand(
      payload: Option[FightEditorApplyChangesPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[FightEditorApplyChangesPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class GenerateAbsoluteCategoryCommand(
      payload: Option[GenerateAbsoluteCategoryPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[GenerateAbsoluteCategoryPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class GenerateBracketsCommand(
      payload: Option[GenerateBracketsPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[GenerateBracketsPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  abstract class PayloadlessCommand() extends InternalCommandProcessorCommand[Any] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
    override def payload: Option[Any] = None
    override val categoryId: Option[String] = None
  }
  final case class UnknownCommand(competitionId: Option[String]) extends PayloadlessCommand
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
  ) extends InternalCommandProcessorCommand[GenerateCategoriesFromRestrictionsPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class GenerateScheduleCommand(
      payload: Option[GenerateSchedulePayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[GenerateSchedulePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class PropagateCompetitorsCommand(
      payload: Option[PropagateCompetitorsPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[PropagateCompetitorsPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class RegistrationPeriodAddRegistrationGroupCommand(
      payload: Option[RegistrationPeriodAddRegistrationGroupPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[RegistrationPeriodAddRegistrationGroupPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class RemoveCompetitorCommand(
      payload: Option[RemoveCompetitorPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[RemoveCompetitorPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class SetFightResultCommand(
      payload: Option[SetFightResultPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[SetFightResultPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateCompetitorCommand(
      payload: Option[UpdateCompetitorPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[UpdateCompetitorPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateRegistrationInfoCommand(
      payload: Option[UpdateRegistrationInfoPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[UpdateRegistrationInfoPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class UpdateStageStatusCommand(
      payload: Option[UpdateStageStatusPayload],
      competitionId: Option[String],
      categoryId: Option[String]
  ) extends InternalCommandProcessorCommand[UpdateStageStatusPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  def createErrorCommandCallbackMessageParameters(commandCallbackTopic: String, correlationId: Option[String], value: Errors.Error): (String, String, Array[Byte]) = {
    (
      commandCallbackTopic,
      correlationId.orNull,
      createErrorCallback(correlationId, value).toByteArray
    )
  }
  def createSuccessCallbackMessageParameters(commandCallbackTopic: String, correlationId: String, numberOfEvents: Int): (String, String, Array[Byte]) = {
    (
      commandCallbackTopic,
      correlationId,
      createSuccessCallback(correlationId, numberOfEvents).toByteArray
    )
  }

  def correlationId(cmd: Command): Option[String] = cmd.messageInfo.flatMap(_.id)
  def correlationId(event: Event): Option[String] = event.messageInfo.flatMap(_.correlationId)

  def createErrorCallback(correlationId: Option[String], value: Errors.Error): CommandCallback = {
    CommandCallback()
      .withResult(CommandExecutionResult.FAIL)
      .withErrorInfo(ErrorCallback().withMessage(s"Error: $value"))
      .update(_.correlationId.setIfDefined(correlationId))
  }
  def createSuccessCallback(correlationId: String, numberOfEvents: Int): CommandCallback = {
    CommandCallback()
      .withResult(CommandExecutionResult.SUCCESS)
      .update(
        _.correlationId := correlationId,
        _.numberOfEvents := numberOfEvents
      )
  }
}
