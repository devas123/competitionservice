package compman.compsrv.model

import cats.Monad
import cats.effect.IO
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.command.Commands
import compman.compsrv.model.event.Events
import compman.compsrv.model.event.Events._
import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.command.CommandType._
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.event.EventType._
import zio.Task

object Mapping {
  trait CommandMapping[F[_]] {
    def mapCommandDto(dto: Command): F[Commands.InternalCommandProcessorCommand[Any]]
  }

  object CommandMapping {
    def apply[F[_]](implicit F: CommandMapping[F]): CommandMapping[F] = F

    val live: CommandMapping[LIO] = (dto: Command) =>
      Task {
        dto.`type` match {
          case CHANGE_COMPETITOR_CATEGORY_COMMAND => Commands.ChangeCompetitorCategoryCommand(
              payload = dto.messageInfo.map(_.getChangeCompetitorCategoryPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case SAVE_ABSOLUTE_CATEGORY_COMMAND => Commands.GenerateAbsoluteCategoryCommand(
              payload = dto.messageInfo.map(_.getGenerateAbsoluteCategoryPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case GENERATE_SCHEDULE_COMMAND => Commands.GenerateScheduleCommand(
              payload = dto.messageInfo.map(_.getGenerateSchedulePayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case GENERATE_BRACKETS_COMMAND => Commands.GenerateBracketsCommand(
              payload = dto.messageInfo.map(_.getGenerateBracketsPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case DROP_SCHEDULE_COMMAND => Commands
              .DropScheduleCommand(competitionId = dto.messageInfo.flatMap(_.competitionId))
          case DROP_ALL_BRACKETS_COMMAND => Commands
              .DropAllBracketsCommand(competitionId = dto.messageInfo.flatMap(_.competitionId))
          case DROP_CATEGORY_BRACKETS_COMMAND => Commands.DropBracketsCommand(
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case START_COMPETITION_COMMAND => Commands
              .StartCompetition(competitionId = dto.messageInfo.flatMap(_.competitionId))
          case STOP_COMPETITION_COMMAND => Commands
              .StopCompetition(competitionId = dto.messageInfo.flatMap(_.competitionId))
          case UPDATE_COMPETITION_PROPERTIES_COMMAND => Commands.UpdateCompetitionProperties(
              payload = dto.messageInfo.map(_.getUpdateCompetionPropertiesPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId)
            )
          case CREATE_COMPETITION_COMMAND => Commands.CreateCompetitionCommand(
              payload = dto.messageInfo.map(_.getCreateCompetitionPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case PUBLISH_COMPETITION_COMMAND => Commands
              .PublishCompetitionCommand(competitionId = dto.messageInfo.flatMap(_.competitionId))
          case UNPUBLISH_COMPETITION_COMMAND => Commands
              .UnpublishCompetitionCommand(competitionId = dto.messageInfo.flatMap(_.competitionId))

          case DELETE_COMPETITION_COMMAND => Commands
              .DeleteCompetition(competitionId = dto.messageInfo.flatMap(_.competitionId))

          case ADD_CATEGORY_COMMAND => Commands.AddCategory(
              payload = dto.messageInfo.map(_.getAddCategoryPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId)
            )
          case GENERATE_CATEGORIES_COMMAND => Commands.GenerateCategoriesFromRestrictionsCommand(
              payload = dto.messageInfo.map(_.getGenerateCategoriesFromRestrictionsPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case DELETE_CATEGORY_COMMAND => Commands.DeleteCategoryCommand(
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case FIGHTS_EDITOR_APPLY_CHANGE => Commands.FightEditorApplyChangesCommand(
              payload = dto.messageInfo.map(_.getFightEditorApplyChangesPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case UPDATE_REGISTRATION_INFO_COMMAND => Commands.UpdateRegistrationInfoCommand(
              payload = dto.messageInfo.map(_.getUpdateRegistrationInfoPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case UPDATE_STAGE_STATUS_COMMAND => Commands.UpdateStageStatusCommand(
              payload = dto.messageInfo.map(_.getUpdateStageStatusPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND => Commands.CategoryRegistrationStatusChangeCommand(
              payload = dto.messageInfo.map(_.getCategoryRegistrationStatusChangePayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND => Commands.ChangeFightOrderCommand(
              payload = dto.messageInfo.map(_.getChangeFightOrderPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case DASHBOARD_SET_FIGHT_RESULT_COMMAND => Commands.SetFightResultCommand(
              payload = dto.messageInfo.map(_.getSetFightResultPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case ADD_COMPETITOR_COMMAND => Commands.AddCompetitorCommand(
              payload = dto.messageInfo.map(_.getAddCompetitorPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case CREATE_FAKE_COMPETITORS_COMMAND => Commands.CreateFakeCompetitors(
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case UPDATE_COMPETITOR_COMMAND => Commands.UpdateCompetitorCommand(
              payload = dto.messageInfo.map(_.getUpdateCompetitorPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case REMOVE_COMPETITOR_COMMAND => Commands.RemoveCompetitorCommand(
              payload = dto.messageInfo.map(_.getRemoveCompetitorPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case PROPAGATE_COMPETITORS_COMMAND => Commands.PropagateCompetitorsCommand(
              payload = dto.messageInfo.map(_.getPropagateCompetitorsPayload),
              competitionId = dto.messageInfo.flatMap(_.competitionId),
              categoryId = dto.messageInfo.flatMap(_.categoryId)
            )
          case ADD_ACADEMY_COMMAND => Commands.AddAcademyCommand(payload = dto.messageInfo.map(_.getAddAcademyPayload))
          case UPDATE_ACADEMY_COMMAND => Commands
              .UpdateAcademyCommand(payload = dto.messageInfo.map(_.getUpdateAcademyPayload))
          case REMOVE_ACADEMY_COMMAND => Commands
              .RemoveAcademyCommand(payload = dto.messageInfo.map(_.getRemoveAcademyPayload))
          case CommandType.UNKNOWN         => Commands.UnknownCommand(dto.messageInfo.flatMap(_.competitionId))
          case _: CommandType.Unrecognized => Commands.UnknownCommand(dto.messageInfo.flatMap(_.competitionId))
        }
      }

  }
  trait EventMapping[F[+_]] {
    def mapEventDto(dto: Event): F[Events.Event[Any]]
  }

  object EventMapping {
    def apply[F[+_]](implicit F: EventMapping[F]): EventMapping[F] = F

    def mapEvent(dto: Event): Events.Event[Any] = {
      dto.`type` match {
        case BRACKETS_GENERATED => BracketsGeneratedEvent(
            dto.messageInfo.map(_.getBracketsGeneratedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )
        case SCHEDULE_GENERATED => ScheduleGeneratedEvent(
            dto.messageInfo.map(_.getScheduleGeneratedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case FIGHTS_ADDED_TO_STAGE => FightsAddedToStageEvent(
            dto.messageInfo.map(_.getFightsAddedToStagePayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case STAGE_STATUS_UPDATED => StageStatusUpdatedEvent(
            dto.messageInfo.map(_.getStageStatusUpdatedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case COMPETITOR_ADDED => CompetitorAddedEvent(
            dto.messageInfo.map(_.getCompetitorAddedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case COMPETITOR_REMOVED => CompetitorRemovedEvent(
            dto.messageInfo.map(_.getCompetitorRemovedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case COMPETITOR_UPDATED => CompetitorUpdatedEvent(
            dto.messageInfo.map(_.getCompetitorUpdatedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case COMPETITOR_CATEGORY_CHANGED => CompetitorCategoryChangedEvent(
            dto.messageInfo.map(_.getChangeCompetitorCategoryPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.localEventNumber
          )

        case COMPETITOR_CATEGORY_ADDED => CompetitorCategoryAddedEvent(
            dto.messageInfo.map(_.getCompetitorCategoryAddedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.localEventNumber
          )

        case CATEGORY_ADDED => CategoryAddedEvent(
            dto.messageInfo.map(_.getCategoryAddedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case CATEGORY_DELETED => CategoryDeletedEvent(
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case CATEGORY_BRACKETS_DROPPED => CategoryBracketsDropped(
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case CATEGORY_REGISTRATION_STATUS_CHANGED => CategoryRegistrationStatusChanged(
            dto.messageInfo.map(_.getCategoryRegistrationStatusChangePayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case COMPETITION_CREATED => CompetitionCreatedEvent(
            dto.messageInfo.map(_.getCompetitionCreatedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case COMPETITION_DELETED =>
          CompetitionDeletedEvent(dto.messageInfo.flatMap(_.competitionId), dto.localEventNumber)

        case COMPETITION_PROPERTIES_UPDATED => CompetitionPropertiesUpdatedEvent(
            dto.messageInfo.map(_.getCompetitionPropertiesUpdatedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.localEventNumber
          )

        case COMPETITORS_PROPAGATED_TO_STAGE => CompetitorsPropagatedToStageEvent(
            dto.messageInfo.map(_.getCompetitorsPropagatedToStagePayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case FIGHTS_START_TIME_UPDATED => FightStartTimeUpdatedEvent(
            dto.messageInfo.map(_.getFightStartTimeUpdatedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case FIGHTS_START_TIME_CLEANED => FightStartTimeCleaned(
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            None,
            dto.localEventNumber
          )

        case FIGHTS_EDITOR_CHANGE_APPLIED => FightEditorChangesAppliedEvent(
            dto.messageInfo.map(_.getFightEditorChangesAppliedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case SCHEDULE_DROPPED => ScheduleDropped(dto.messageInfo.flatMap(_.competitionId), dto.localEventNumber)

        case REGISTRATION_INFO_UPDATED => RegistrationInfoUpdatedEvent(
            dto.messageInfo.map(_.getRegistrationInfoUpdatedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case DASHBOARD_FIGHT_RESULT_SET => FightResultSet(
            dto.messageInfo.map(_.getSetFightResultPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            None,
            dto.localEventNumber
          )

        case DASHBOARD_FIGHT_COMPETITORS_ASSIGNED => FightCompetitorsAssignedEvent(
            dto.messageInfo.map(_.getFightCompetitorsAssignedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case DASHBOARD_STAGE_RESULT_SET => StageResultSetEvent(
            dto.messageInfo.map(_.getStageResultSetPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case MATS_UPDATED => MatsUpdatedEvent(
            dto.messageInfo.map(_.getMatsUpdatedPayload),
            dto.messageInfo.flatMap(_.competitionId),
            dto.messageInfo.flatMap(_.categoryId),
            dto.localEventNumber
          )

        case ACADEMY_ADDED =>
          AcademyAddedEvent(payload = dto.messageInfo.map(_.getAddAcademyPayload), dto.localEventNumber)
        case ACADEMY_UPDATED =>
          AcademyUpdatedEvent(payload = dto.messageInfo.map(_.getUpdateAcademyPayload), dto.localEventNumber)
        case ACADEMY_REMOVED =>
          AcademyRemovedEvent(payload = dto.messageInfo.map(_.getRemoveAcademyPayload), dto.localEventNumber)
        case EventType.UNKNOWN => Events.UnknownEvent(dto.messageInfo.flatMap(_.competitionId), dto.localEventNumber)
        case _: EventType.Unrecognized => Events
            .UnknownEvent(dto.messageInfo.flatMap(_.competitionId), dto.localEventNumber)
      }
    }

    def create[F[+_]: Monad]: EventMapping[F] = (dto: Event) => Monad[F].pure { mapEvent(dto) }

    val live: EventMapping[IO] = create[IO]

    def mapEventDto[F[+_]: EventMapping](dto: Event): F[Events.Event[Any]] = EventMapping[F].mapEventDto(dto)
  }

  def mapCommandDto[F[+_]: CommandMapping](command: Command): F[Commands.InternalCommandProcessorCommand[Any]] =
    CommandMapping[F].mapCommandDto(command)
}
