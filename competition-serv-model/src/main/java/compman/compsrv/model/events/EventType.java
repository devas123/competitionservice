package compman.compsrv.model.events;

public enum EventType {
    //Category and competition
    ERROR_EVENT,
    ALL_BRACKETS_DROPPED,
    BRACKETS_GENERATED,
    SCHEDULE_GENERATED,


    COMPETITOR_ADDED,
    COMPETITOR_REMOVED,
    COMPETITOR_UPDATED,
    COMPETITOR_CATEGORY_CHANGED,

    CATEGORY_ADDED,
    CATEGORY_DELETED,
    CATEGORY_BRACKETS_DROPPED,

    COMPETITION_STARTED,
    COMPETITION_CREATED,
    COMPETITION_DELETED,
    COMPETITION_PUBLISHED,
    COMPETITION_UNPUBLISHED,
    COMPETITION_STOPPED,
    COMPETITION_PROPERTIES_UPDATED,

    FIGHTS_START_TIME_UPDATED,
    FIGHTS_EDITOR_CHANGE_APPLIED,

    SCHEDULE_DROPPED,

    REGISTRATION_PERIOD_ADDED,
    REGISTRATION_INFO_UPDATED,
    REGISTRATION_PERIOD_DELETED,
    REGISTRATION_GROUP_CREATED,
    REGISTRATION_GROUP_DELETED,
    REGISTRATION_GROUP_CATEGORIES_ASSIGNED,

    DASHBOARD_CREATED,
    DASHBOARD_DELETED,

    //Dummy
    DUMMY,

    //Internal
    INTERNAL_COMPETITION_INFO

}