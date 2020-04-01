package compman.compsrv.model.events;

public enum EventType {
    //Category and competition
    ERROR_EVENT,
    BRACKETS_GENERATED,
    SCHEDULE_GENERATED,
    FIGHTS_ADDED_TO_STAGE,


    COMPETITOR_ADDED,
    COMPETITOR_REMOVED,
    COMPETITOR_UPDATED,
    COMPETITOR_CATEGORY_CHANGED,

    CATEGORY_ADDED,
    CATEGORY_DELETED,
    CATEGORY_BRACKETS_DROPPED,
    CATEGORY_REGISTRATION_STATUS_CHANGED,

    COMPETITION_STARTED,
    COMPETITION_CREATED,
    COMPETITION_DELETED,
    COMPETITION_PUBLISHED,
    COMPETITION_UNPUBLISHED,
    COMPETITION_STOPPED,
    COMPETITION_PROPERTIES_UPDATED,
    COMPETITORS_PROPAGATED_TO_STAGE,

    FIGHTS_START_TIME_UPDATED,
    FIGHTS_EDITOR_CHANGE_APPLIED,

    SCHEDULE_DROPPED,

    REGISTRATION_PERIOD_ADDED,
    REGISTRATION_INFO_UPDATED,
    REGISTRATION_PERIOD_DELETED,
    REGISTRATION_GROUP_ADDED,
    REGISTRATION_GROUP_ADDED_TO_REGISTRATION_PERIOD,
    REGISTRATION_GROUP_DELETED,
    REGISTRATION_GROUP_CATEGORIES_ASSIGNED,

    DASHBOARD_FIGHT_RESULT_SET,
    DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
    DASHBOARD_FIGHT_ORDER_CHANGED,
    DASHBOARD_STAGE_RESULT_SET,

    //Dummy
    DUMMY,

    //Internal
    INTERNAL_COMPETITION_INFO

}