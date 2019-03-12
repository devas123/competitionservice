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
    COMPETITORS_MOVED,

    SCHEDULE_DROPPED,

    REGISTRATION_PERIOD_ADDED,
    REGISTRATION_PERIOD_DELETED,
    REGISTRATION_GROUP_ADDED,
    REGISTRATION_GROUP_DELETED,

    //Dummy
    DUMMY,

    //FORWARD STATE
    INTERNAL_STATE_SNAPSHOT_CREATED
}