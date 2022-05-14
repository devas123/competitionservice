package compman.compsrv.model

import cats.Monad
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.command.Commands
import compman.compsrv.model.event.Events
import compman.compsrv.model.event.Events._
import compservice.model.protobuf.command.Command
import compservice.model.protobuf.command.CommandType._
import compservice.model.protobuf.event.Event
import compservice.model.protobuf.event.EventType._
import zio.Task

object Mapping {
  trait CommandMapping[F[_]] {
    def mapCommandDto(commandDTO: Command): F[Commands.InternalCommandProcessorCommand[Any]]
  }

  object CommandMapping {
    def apply[F[_]](implicit F: CommandMapping[F]): CommandMapping[F] = F

    val live: CommandMapping[LIO] = (dto: Command) =>
      Task {
        dto.`type` match {
          case CHANGE_COMPETITOR_CATEGORY_COMMAND => Commands.ChangeCompetitorCategoryCommand(
              payload = dto.messageInfo.map(_.getChangeCompetitorCategoryPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case SAVE_ABSOLUTE_CATEGORY_COMMAND => Commands.GenerateAbsoluteCategoryCommand(
              payload = dto.messageInfo.map(_.getGenerateAbsoluteCategoryPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case GENERATE_SCHEDULE_COMMAND => Commands.GenerateScheduleCommand(
              payload = dto.messageInfo.map(_.getGenerateSchedulePayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case GENERATE_BRACKETS_COMMAND => Commands.GenerateBracketsCommand(
              payload = dto.messageInfo.map(_.getGenerateBracketsPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case DROP_SCHEDULE_COMMAND => Commands
              .DropScheduleCommand(competitionId = dto.messageInfo.map(_.competitionId))
          case DROP_ALL_BRACKETS_COMMAND => Commands
              .DropAllBracketsCommand(competitionId = dto.messageInfo.map(_.competitionId))
          case DROP_CATEGORY_BRACKETS_COMMAND => Commands.DropBracketsCommand(
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case START_COMPETITION_COMMAND => Commands
              .StartCompetition(competitionId = dto.messageInfo.map(_.competitionId))
          case STOP_COMPETITION_COMMAND => Commands.StopCompetition(competitionId = dto.messageInfo.map(_.competitionId))
          case UPDATE_COMPETITION_PROPERTIES_COMMAND => Commands.UpdateCompetitionProperties(
              payload = dto.messageInfo.map(_.getUpdateCompetionPropertiesPayload),
              competitionId = dto.messageInfo.map(_.competitionId)
            )
          case CREATE_COMPETITION_COMMAND => Commands.CreateCompetitionCommand(
              payload = dto.messageInfo.map(_.getCreateCompetitionPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case PUBLISH_COMPETITION_COMMAND => Commands
              .PublishCompetition(competitionId = dto.messageInfo.map(_.competitionId))
          case UNPUBLISH_COMPETITION_COMMAND => Commands
              .UnpublishCompetition(competitionId = dto.messageInfo.map(_.competitionId))

          case DELETE_COMPETITION_COMMAND => Commands
              .DeleteCompetition(competitionId = dto.messageInfo.map(_.competitionId))

          case ADD_CATEGORY_COMMAND => Commands.AddCategory(
              payload = dto.messageInfo.map(_.getAddCategoryPayload),
              competitionId = dto.messageInfo.map(_.competitionId)
            )
          case GENERATE_CATEGORIES_COMMAND => Commands.GenerateCategoriesFromRestrictionsCommand(
              payload =  dto.messageInfo.map(_.getGenerateCategoriesFromRestrictionsPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case DELETE_CATEGORY_COMMAND => Commands.DeleteCategoryCommand(
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case FIGHTS_EDITOR_APPLY_CHANGE => Commands.FightEditorApplyChangesCommand(
              payload = dto.messageInfo.map(_.getFightEditorApplyChangesPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case ADD_REGISTRATION_PERIOD_COMMAND => Commands.AddRegistrationPeriodCommand(
              payload = dto.messageInfo.map(_.getAddRegistrationPeriodPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case ADD_REGISTRATION_GROUP_COMMAND => Commands.AddRegistrationGroupCommand(
              payload = dto.messageInfo.map(_.getAddRegistrationGroupPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case ADD_REGISTRATION_GROUP_TO_REGISTRATION_PERIOD_COMMAND => Commands
              .RegistrationPeriodAddRegistrationGroupCommand(
                payload = dto.messageInfo.map(_.getRegistrationPeriodAddRegistrationGroupPayload),
                competitionId = dto.messageInfo.map(_.competitionId),
                categoryId = dto.messageInfo.map(_.categoryId)
              )
          case DELETE_REGISTRATION_GROUP_COMMAND => Commands.DeleteRegistrationGroupCommand(
              payload = dto.messageInfo.map(_.getDeleteRegistrationGroupPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case DELETE_REGISTRATION_PERIOD_COMMAND => Commands.DeleteRegistrationPeriodCommand(
              payload = dto.messageInfo.map(_.getDeleteRegistrationPeriodPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND => Commands.AssignRegistrationGroupCategoriesCommand(
              payload = dto.messageInfo.map(_.getAssignRegistrationGroupCategoriesPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case UPDATE_REGISTRATION_INFO_COMMAND => Commands.UpdateRegistrationInfoCommand(
              payload = dto.messageInfo.map(_.getUpdateRegistrationInfoPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case UPDATE_STAGE_STATUS_COMMAND => Commands.UpdateStageStatusCommand(
              payload = dto.messageInfo.map(_.getUpdateStageStatusPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND => Commands.CategoryRegistrationStatusChangeCommand(
              payload = dto.messageInfo.map(_.getCategoryRegistrationStatusChangePayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND => Commands.ChangeFightOrderCommand(
              payload = dto.messageInfo.map(_.getChangeFightOrderPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case DASHBOARD_SET_FIGHT_RESULT_COMMAND => Commands.SetFightResultCommand(
              payload = dto.messageInfo.map(_.getSetFightResultPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case ADD_COMPETITOR_COMMAND => Commands.AddCompetitorCommand(
              payload = dto.messageInfo.map(_.getAddCompetitorPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case CREATE_FAKE_COMPETITORS_COMMAND => Commands.CreateFakeCompetitors(
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case UPDATE_COMPETITOR_COMMAND => Commands.UpdateCompetitorCommand(
              payload = dto.messageInfo.map(_.getUpdateCompetitorPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case REMOVE_COMPETITOR_COMMAND => Commands.RemoveCompetitorCommand(
              payload = dto.messageInfo.map(_.getRemoveCompetitorPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case PROPAGATE_COMPETITORS_COMMAND => Commands.PropagateCompetitorsCommand(
              payload = dto.messageInfo.map(_.getPropagateCompetitorsPayload),
              competitionId = dto.messageInfo.map(_.competitionId),
              categoryId = dto.messageInfo.map(_.categoryId)
            )
          case ADD_ACADEMY_COMMAND => Commands.AddAcademyCommand(
            payload = dto.messageInfo.map(_.getAddAcademyPayload)
          )
          case UPDATE_ACADEMY_COMMAND => Commands.UpdateAcademyCommand(
            payload = dto.messageInfo.map(_.getUpdateAcademyPayload)
          )
          case REMOVE_ACADEMY_COMMAND => Commands.RemoveAcademyCommand(
            payload = dto.messageInfo.map(_.getRemoveAcademyPayload)
          )
        }
      }

  }
  trait EventMapping[F[+_]] {
    def mapEventDto(dto: Event): F[Events.Event[Any]]
  }

  object EventMapping {
    def apply[F[+_]](implicit F: EventMapping[F]): EventMapping[F] = F

    def create[F[+_]: Monad]: EventMapping[F] = (dto: Event) =>
      Monad[F].pure {
        dto.`type` match {
          case FIGHT_ORDER_CHANGED => FightOrderChangedEvent(
              dto.messageInfo.map(_.getChangeFightOrderPayload),
            dto.messageInfo.map(_.competitionId),
            dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )
          case BRACKETS_GENERATED => BracketsGeneratedEvent(
              dto.messageInfo.map(_.getBracketsGeneratedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )
          case SCHEDULE_GENERATED => ScheduleGeneratedEvent(
              dto.messageInfo.map(_.getScheduleGeneratedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case FIGHTS_ADDED_TO_STAGE => FightsAddedToStageEvent(
              dto.messageInfo.map(_.getFightsAddedToStagePayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case STAGE_STATUS_UPDATED => StageStatusUpdatedEvent(
              dto.messageInfo.map(_.getStageStatusUpdatedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case COMPETITOR_ADDED => CompetitorAddedEvent(
              dto.messageInfo.map(_.getCompetitorAddedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case COMPETITOR_REMOVED => CompetitorRemovedEvent(
              dto.messageInfo.map(_.getCompetitorRemovedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case COMPETITOR_UPDATED => CompetitorUpdatedEvent(
              dto.messageInfo.map(_.getCompetitorUpdatedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case COMPETITOR_CATEGORY_CHANGED => CompetitorCategoryChangedEvent(
              dto.messageInfo.map(_.getChangeCompetitorCategoryPayload),
              dto.messageInfo.map(_.competitionId),
              dto.localEventNumber
            )

          case COMPETITOR_CATEGORY_ADDED => CompetitorCategoryAddedEvent(
              dto.messageInfo.map(_.getCompetitorCategoryAddedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.localEventNumber
            )

          case CATEGORY_ADDED => CategoryAddedEvent(
              dto.messageInfo.map(_.getCategoryAddedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case CATEGORY_DELETED => CategoryDeletedEvent(
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case CATEGORY_BRACKETS_DROPPED => CategoryBracketsDropped(
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case CATEGORY_REGISTRATION_STATUS_CHANGED => CategoryRegistrationStatusChanged(
              dto.messageInfo.map(_.getCategoryRegistrationStatusChangePayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case COMPETITION_CREATED => CompetitionCreatedEvent(
              dto.messageInfo.map(_.getCompetitionCreatedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case COMPETITION_DELETED =>
            CompetitionDeletedEvent(dto.messageInfo.map(_.competitionId), dto.localEventNumber)

          case COMPETITION_PROPERTIES_UPDATED => CompetitionPropertiesUpdatedEvent(
              dto.messageInfo.map(_.getCompetitionPropertiesUpdatedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.localEventNumber
            )

          case COMPETITORS_PROPAGATED_TO_STAGE => CompetitorsPropagatedToStageEvent(
              dto.messageInfo.map(_.getCompetitorsPropagatedToStagePayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case FIGHTS_START_TIME_UPDATED => FightStartTimeUpdatedEvent(
              dto.messageInfo.map(_.getFightStartTimeUpdatedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case FIGHTS_START_TIME_CLEANED => FightStartTimeCleaned(
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              None,
              dto.localEventNumber
            )

          case FIGHTS_EDITOR_CHANGE_APPLIED => FightEditorChangesAppliedEvent(
              dto.messageInfo.map(_.getFightEditorChangesAppliedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case SCHEDULE_DROPPED => ScheduleDropped(dto.messageInfo.map(_.competitionId), dto.localEventNumber)

          case REGISTRATION_PERIOD_ADDED => RegistrationPeriodAddedEvent(
              dto.messageInfo.map(_.getRegistrationPeriodAddedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case REGISTRATION_INFO_UPDATED => RegistrationInfoUpdatedEvent(
              dto.messageInfo.map(_.getRegistrationInfoUpdatedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case REGISTRATION_PERIOD_DELETED => RegistrationPeriodDeletedEvent(
              dto.messageInfo.map(_.getRegistrationPeriodDeletedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case REGISTRATION_GROUP_ADDED => RegistrationGroupAddedEvent(
              dto.messageInfo.map(_.getRegistrationGroupAddedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case REGISTRATION_GROUP_DELETED => RegistrationGroupDeletedEvent(
              dto.messageInfo.map(_.getRegistrationGroupDeletedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case REGISTRATION_GROUP_CATEGORIES_ASSIGNED => RegistrationGroupCategoriesAssignedEvent(
              dto.messageInfo.map(_.getRegistrationGroupCategoriesAssignedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case DASHBOARD_FIGHT_RESULT_SET => FightResultSet(
              dto.messageInfo.map(_.getSetFightResultPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              None,
              dto.localEventNumber
            )

          case DASHBOARD_FIGHT_COMPETITORS_ASSIGNED => FightCompetitorsAssignedEvent(
              dto.messageInfo.map(_.getFightCompetitorsAssignedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case DASHBOARD_STAGE_RESULT_SET => StageResultSetEvent(
              dto.messageInfo.map(_.getStageResultSetPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case MATS_UPDATED => MatsUpdatedEvent(
              dto.messageInfo.map(_.getMatsUpdatedPayload),
              dto.messageInfo.map(_.competitionId),
              dto.messageInfo.map(_.categoryId),
              dto.localEventNumber
            )

          case ACADEMY_ADDED => AcademyAddedEvent(
            payload = dto.messageInfo.map(_.getAddAcademyPayload),
            dto.localEventNumber
          )
          case ACADEMY_UPDATED => AcademyUpdatedEvent(
            payload = dto.messageInfo.map(_.getUpdateAcademyPayload),
            dto.localEventNumber
          )
          case ACADEMY_REMOVED => AcademyRemovedEvent(
            payload = dto.messageInfo.map(_.getRemoveAcademyPayload),
            dto.localEventNumber
          )
        }
      }

    val live: EventMapping[LIO] = {
      import zio.interop.catz._
      create[LIO]
    }

    def mapEventDto[F[+_]: EventMapping](dto: Event): F[Events.Event[Any]] = EventMapping[F]
      .mapEventDto(dto)
  }

  def mapCommandDto[F[+_]: CommandMapping](command: Command): F[Commands.InternalCommandProcessorCommand[Any]] = CommandMapping[F]
    .mapCommandDto(command)
}
