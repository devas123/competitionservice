package compman.compsrv.model.events;

import compman.compsrv.model.commands.payload.CategoryRegistrationStatusChangePayload;
import compman.compsrv.model.commands.payload.Payload;
import compman.compsrv.model.commands.payload.SetFightResultPayload;
import compman.compsrv.model.events.payload.*;

public enum EventType {
    //Category and competition
    ERROR_EVENT(ErrorEventPayload.class),
    BRACKETS_GENERATED(BracketsGeneratedPayload.class),
    SCHEDULE_GENERATED(ScheduleGeneratedPayload.class),
    FIGHTS_ADDED_TO_STAGE(FightsAddedToStagePayload.class),
    STAGE_STATUS_UPDATED(StageStatusUpdatedPayload.class),


    COMPETITOR_ADDED(CompetitorAddedPayload.class),
    COMPETITOR_REMOVED(CompetitorRemovedPayload.class),
    COMPETITOR_UPDATED(CompetitorUpdatedPayload.class),
    COMPETITOR_CATEGORY_CHANGED(null),

    CATEGORY_ADDED(CategoryAddedPayload.class),
    CATEGORY_DELETED(null),
    CATEGORY_BRACKETS_DROPPED(null),
    CATEGORY_NUMBER_OF_COMPETITORS_INCREASED(null),
    CATEGORY_NUMBER_OF_COMPETITORS_DECREASED(null),
    CATEGORY_REGISTRATION_STATUS_CHANGED(CategoryRegistrationStatusChangePayload.class),

    COMPETITION_STARTED(CompetitionStatusUpdatedPayload.class),
    COMPETITION_CREATED(CompetitionCreatedPayload.class),
    COMPETITION_DELETED(null),
    COMPETITION_PUBLISHED(CompetitionStatusUpdatedPayload.class),
    COMPETITION_UNPUBLISHED(CompetitionStatusUpdatedPayload.class),
    COMPETITION_STOPPED(CompetitionStatusUpdatedPayload.class),
    COMPETITION_PROPERTIES_UPDATED(CompetitionPropertiesUpdatedPayload.class),
    COMPETITORS_PROPAGATED_TO_STAGE(CompetitorsPropagatedToStagePayload.class),

    FIGHTS_START_TIME_UPDATED(FightStartTimeUpdatedPayload.class),
    FIGHTS_EDITOR_CHANGE_APPLIED(FightEditorChangesAppliedPayload.class),

    SCHEDULE_DROPPED(null),

    REGISTRATION_PERIOD_ADDED(RegistrationPeriodAddedPayload.class),
    REGISTRATION_INFO_UPDATED(RegistrationInfoUpdatedPayload.class),
    REGISTRATION_PERIOD_DELETED(RegistrationPeriodDeletedPayload.class),
    REGISTRATION_GROUP_ADDED(RegistrationGroupAddedPayload.class),
    REGISTRATION_GROUP_ADDED_TO_REGISTRATION_PERIOD(null),
    REGISTRATION_GROUP_DELETED(RegistrationGroupDeletedPayload.class),
    REGISTRATION_GROUP_CATEGORIES_ASSIGNED(RegistrationGroupCategoriesAssignedPayload.class),

    DASHBOARD_FIGHT_RESULT_SET(SetFightResultPayload.class),
    DASHBOARD_FIGHT_COMPETITORS_ASSIGNED(FightCompetitorsAssignedPayload.class),
    DASHBOARD_FIGHT_ORDER_CHANGED(DashboardFightOrderChangedPayload.class),
    DASHBOARD_STAGE_RESULT_SET(StageResultSetPayload.class),

    //Dummy
    DUMMY(null),

    //Internal
    INTERNAL_COMPETITION_INFO(CompetitionInfoPayload.class);

    public final Class<? extends Payload> payloadClass;

    EventType(Class<? extends Payload> payloadClass) {
        this.payloadClass = payloadClass;
    }
}