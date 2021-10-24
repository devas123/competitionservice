package compman.compsrv.model.events;

public enum EventType {
    //Category and competition
    BRACKETS_GENERATED,
    SCHEDULE_GENERATED,
    FIGHTS_ADDED_TO_STAGE,
    STAGE_STATUS_UPDATED,


    COMPETITOR_ADDED,
    COMPETITOR_REMOVED,
    COMPETITOR_UPDATED,
    COMPETITOR_CATEGORY_CHANGED,
    COMPETITOR_CATEGORY_ADDED,

    CATEGORY_ADDED,
    CATEGORY_DELETED,
    CATEGORY_BRACKETS_DROPPED,
    CATEGORY_REGISTRATION_STATUS_CHANGED,

    COMPETITION_CREATED,
    COMPETITION_DELETED,
    COMPETITION_PROPERTIES_UPDATED,
    COMPETITORS_PROPAGATED_TO_STAGE,

    FIGHTS_START_TIME_UPDATED,
    FIGHT_PROPERTIES_UPDATED,
    FIGHTS_START_TIME_CLEANED,
    FIGHTS_EDITOR_CHANGE_APPLIED,

    SCHEDULE_DROPPED,

    REGISTRATION_PERIOD_ADDED,
    REGISTRATION_INFO_UPDATED,
    REGISTRATION_PERIOD_DELETED,
    REGISTRATION_GROUP_ADDED,
    REGISTRATION_GROUP_DELETED,
    REGISTRATION_GROUP_CATEGORIES_ASSIGNED,

    DASHBOARD_FIGHT_RESULT_SET,
    DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
    DASHBOARD_STAGE_RESULT_SET,
    MATS_UPDATED
}