package compman.compsrv.logic

import compman.compsrv.model.command.Commands
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.CommandType._
import compman.compsrv.model.commands.payload._
import compman.compsrv.model.event.Events
import compman.compsrv.model.event.Events._
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType._
import compman.compsrv.model.events.payload._
import zio.Task

import scala.util.Try

object Mapping {
  trait CommandMapping[F[_]] {
    def mapCommandDto(commandDTO: CommandDTO): F[Commands.Command[Payload]]
  }

  object CommandMapping {
    def apply[F[_]](implicit F: CommandMapping[F]): CommandMapping[F] = F

    val live: CommandMapping[Task] =
      (commandDTO: CommandDTO) =>
        Task {
          commandDTO.getType match {
            case CHANGE_COMPETITOR_CATEGORY_COMMAND =>
              Commands.ChangeCompetitorCategoryCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[ChangeCompetitorCategoryPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case SAVE_ABSOLUTE_CATEGORY_COMMAND =>
              Commands.GenerateAbsoluteCategoryCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[GenerateAbsoluteCategoryPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case GENERATE_SCHEDULE_COMMAND =>
              Commands.GenerateScheduleCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[GenerateSchedulePayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case GENERATE_BRACKETS_COMMAND =>
              Commands.GenerateBracketsCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[GenerateBracketsPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case DROP_SCHEDULE_COMMAND =>
              Commands.DropScheduleCommand(competitionId = Option(commandDTO.getCompetitionId))
            case DROP_ALL_BRACKETS_COMMAND =>
              Commands.DropAllBracketsCommand(competitionId = Option(commandDTO.getCompetitionId))
            case DROP_CATEGORY_BRACKETS_COMMAND =>
              Commands.DropBracketsCommand(
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case START_COMPETITION_COMMAND =>
              Commands.StartCompetition(competitionId = Option(commandDTO.getCompetitionId))
            case STOP_COMPETITION_COMMAND =>
              Commands.StopCompetition(competitionId = Option(commandDTO.getCompetitionId))
            case UPDATE_COMPETITION_PROPERTIES_COMMAND =>
              Commands.UpdateCompetitionProperties(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[UpdateCompetionPropertiesPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId)
              )
            case CREATE_COMPETITION_COMMAND =>
              Commands.CreateCompetitionCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[CreateCompetitionPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case PUBLISH_COMPETITION_COMMAND =>
              Commands.PublishCompetition(competitionId = Option(commandDTO.getCompetitionId))
            case UNPUBLISH_COMPETITION_COMMAND =>
              Commands.UnpublishCompetition(competitionId = Option(commandDTO.getCompetitionId))

            case DELETE_COMPETITION_COMMAND =>
              Commands.DeleteCompetition(competitionId = Option(commandDTO.getCompetitionId))

            case ADD_CATEGORY_COMMAND =>
              Commands.AddCategory(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[AddCategoryPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId)
              )
            case GENERATE_CATEGORIES_COMMAND =>
              Commands.GenerateCategoriesFromRestrictionsCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[GenerateCategoriesFromRestrictionsPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case DELETE_CATEGORY_COMMAND =>
              Commands.DeleteCategory(
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case FIGHTS_EDITOR_APPLY_CHANGE =>
              Commands.FightEditorApplyChangesCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[FightEditorApplyChangesPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case ADD_REGISTRATION_PERIOD_COMMAND =>
              Commands.AddRegistrationPeriodCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[AddRegistrationPeriodPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case ADD_REGISTRATION_GROUP_COMMAND =>
              Commands.AddRegistrationGroupCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[AddRegistrationGroupPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case ADD_REGISTRATION_GROUP_TO_REGISTRATION_PERIOD_COMMAND =>
              Commands.RegistrationPeriodAddRegistrationGroupCommand(
                payload =
                  Try {
                    commandDTO
                      .getPayload
                      .asInstanceOf[RegistrationPeriodAddRegistrationGroupPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case DELETE_REGISTRATION_GROUP_COMMAND =>
              Commands.DeleteRegistrationGroupCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[DeleteRegistrationGroupPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case DELETE_REGISTRATION_PERIOD_COMMAND =>
              Commands.DeleteRegistrationPeriodCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[DeleteRegistrationPeriodPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND =>
              Commands.AssignRegistrationGroupCategoriesCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[AssignRegistrationGroupCategoriesPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case UPDATE_REGISTRATION_INFO_COMMAND =>
              Commands.UpdateRegistrationInfoCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[UpdateRegistrationInfoPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case UPDATE_STAGE_STATUS_COMMAND =>
              Commands.UpdateStageStatusCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[UpdateStageStatusPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND =>
              Commands.CategoryRegistrationStatusChangeCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[CategoryRegistrationStatusChangePayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND =>
              Commands.ChangeFightOrderCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[ChangeFightOrderPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case DASHBOARD_SET_FIGHT_RESULT_COMMAND =>
              Commands.SetFightResultCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[SetFightResultPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case ADD_COMPETITOR_COMMAND =>
              Commands.AddCompetitorCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[AddCompetitorPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case CREATE_FAKE_COMPETITORS_COMMAND =>
              Commands.CreateFakeCompetitors(
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case UPDATE_COMPETITOR_COMMAND =>
              Commands.UpdateCompetitorCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[UpdateCompetitorPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case REMOVE_COMPETITOR_COMMAND =>
              Commands.RemoveCompetitorCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[RemoveCompetitorPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
            case PROPAGATE_COMPETITORS_COMMAND =>
              Commands.PropagateCompetitorsCommand(
                payload =
                  Try {
                    commandDTO.getPayload.asInstanceOf[PropagateCompetitorsPayload]
                  }.toOption,
                competitionId = Option(commandDTO.getCompetitionId),
                categoryId = Option(commandDTO.getCategoryId)
              )
          }
        }

  }
  trait EventMapping[F[+_]] {
    def mapEventDto(eventDto: EventDTO): F[Events.Event[Payload]]
    def mapEvent(event: Events.Event[Payload]): F[EventDTO]
  }

  object EventMapping {
    def apply[F[+_]](implicit F: EventMapping[F]): EventMapping[F] = F

    val live: EventMapping[Task] =
      new EventMapping[Task] {
        override def mapEventDto(eventDto: EventDTO): Task[Events.Event[Payload]] = Task {
          eventDto.getType match {
            case BRACKETS_GENERATED =>
              BracketsGeneratedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[BracketsGeneratedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )
            case SCHEDULE_GENERATED =>
              ScheduleGeneratedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[ScheduleGeneratedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case FIGHTS_ADDED_TO_STAGE =>
              FightsAddedToStageEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[FightsAddedToStagePayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case STAGE_STATUS_UPDATED =>
              StageStatusUpdatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[StageStatusUpdatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case COMPETITOR_ADDED =>
              CompetitorAddedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[CompetitorAddedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case COMPETITOR_REMOVED =>
              CompetitorRemovedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[CompetitorRemovedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case COMPETITOR_UPDATED =>
              CompetitorUpdatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[CompetitorUpdatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case COMPETITOR_CATEGORY_CHANGED =>
              CompetitorCategoryChangedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[ChangeCompetitorCategoryPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                eventDto.getVersion
              )

            case CATEGORY_ADDED =>
              CategoryAddedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[CategoryAddedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case CATEGORY_DELETED =>
              CategoryDeletedEvent(
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case CATEGORY_BRACKETS_DROPPED =>
              CategoryBracketsDropped(
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case CATEGORY_REGISTRATION_STATUS_CHANGED =>
              CategoryRegistrationStatusChanged(
                Try {
                  eventDto.getPayload.asInstanceOf[CategoryRegistrationStatusChangePayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case COMPETITION_CREATED =>
              CompetitionCreatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[CompetitionCreatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case COMPETITION_DELETED =>
              CompetitionDeletedEvent(Option(eventDto.getCompetitionId), eventDto.getVersion)

            case COMPETITION_PROPERTIES_UPDATED =>
              CompetitionPropertiesUpdatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[CompetitionPropertiesUpdatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                eventDto.getVersion
              )

            case COMPETITORS_PROPAGATED_TO_STAGE =>
              CompetitorsPropagatedToStageEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[CompetitorsPropagatedToStagePayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case FIGHTS_START_TIME_UPDATED =>
              FightStartTimeUpdatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[FightStartTimeUpdatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case FIGHT_PROPERTIES_UPDATED =>
              FightPropertiesUpdatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[FightPropertiesUpdatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case FIGHTS_START_TIME_CLEANED =>
              FightStartTimeCleaned(
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                None,
                eventDto.getVersion
              )

            case FIGHTS_EDITOR_CHANGE_APPLIED =>
              FightEditorChangesAppliedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[FightEditorChangesAppliedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case SCHEDULE_DROPPED =>
              ScheduleDropped(Option(eventDto.getCompetitionId), eventDto.getVersion)

            case REGISTRATION_PERIOD_ADDED =>
              RegistrationPeriodAddedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[RegistrationPeriodAddedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case REGISTRATION_INFO_UPDATED =>
              RegistrationInfoUpdatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[RegistrationInfoUpdatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case REGISTRATION_PERIOD_DELETED =>
              RegistrationPeriodDeletedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[RegistrationPeriodDeletedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case REGISTRATION_GROUP_ADDED =>
              RegistrationGroupAddedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[RegistrationGroupAddedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case REGISTRATION_GROUP_DELETED =>
              RegistrationGroupDeletedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[RegistrationGroupDeletedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case REGISTRATION_GROUP_CATEGORIES_ASSIGNED =>
              RegistrationGroupCategoriesAssignedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[RegistrationGroupCategoriesAssignedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case DASHBOARD_FIGHT_RESULT_SET =>
              FightResultSet(
                Try {
                  eventDto.getPayload.asInstanceOf[SetFightResultPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                None,
                eventDto.getVersion
              )

            case DASHBOARD_FIGHT_COMPETITORS_ASSIGNED =>
              FightCompetitorsAssignedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[FightCompetitorsAssignedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case DASHBOARD_STAGE_RESULT_SET =>
              StageResultSetEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[StageResultSetPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )

            case MATS_UPDATED =>
              MatsUpdatedEvent(
                Try {
                  eventDto.getPayload.asInstanceOf[MatsUpdatedPayload]
                }.toOption,
                Option(eventDto.getCompetitionId),
                Option(eventDto.getCategoryId),
                eventDto.getVersion
              )
          }
        }

        override def mapEvent(event: Events.Event[Payload]): Task[EventDTO] = Task {
          val e = new EventDTO()
          event.competitorId.foreach(e.setCompetitorId)
          event.categoryId.foreach(e.setCategoryId)
          event.competitionId.foreach(e.setCompetitionId)
          e.setVersion(event.sequenceNumber)
          event.payload.foreach(e.setPayload)
          e
        }
      }
    def mapEventDto[F[+_]: EventMapping](eventDto: EventDTO): F[Events.Event[Payload]] =
      EventMapping[F].mapEventDto(eventDto)
    def mapEvent[F[+_]: EventMapping](event: Events.Event[Payload]): F[EventDTO] = EventMapping[F]
      .mapEvent(event)
  }

  def mapCommandDto[F[+_]: CommandMapping](commandDTO: CommandDTO): F[Commands.Command[Payload]] =
    CommandMapping[F].mapCommandDto(commandDTO)
}
