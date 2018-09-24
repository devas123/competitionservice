package compman.compsrv.model.es.events

enum class
EventType {
    //Category and competition
    SCORE_UPDATE,
    ABSOLUTE_CATEGORY_SAVED,
    ERROR_EVENT,
    ALL_BRACKETS_DROPPED,
    START_FIGHT,
    CHANGE_FIGHT_ORDER,
    FIGHT_RESULT_UPDATE,
    FIGHT_COMPETITORS_UPDATE,
    BRACKETS_GENERATED,
    BRACKETS_SAVED,
    SCHEDULE_GENERATED,
    SCHEDULE_SAVED,
    OVERWRITE_BRACKETS,
    CHANGE_MAT_EVENT,
    CHANGE_ORDER_EVENT,
    UPDATE_FIGHT_STAGE_EVENT,
    COMPETITOR_ADDED,

    INTERNAL_COMPETITOR_ADDED,
    INTERNAL_COMPETITOR_REMOVED,
    INTERNAL_COMPETITOR_CATEGORY_CHANGED,
    INTERNAL_ALL_BRACKETS_DROPPED,
    COMPETITOR_REMOVED,
    COMPETITOR_UPDATED,
    COMPETITOR_CATEGORY_CHANGED,
    CATEGORY_ADDED,

    CATEGORY_DELETED,
    CATEGORY_BRACKETS_DROPPED,
    CATEGORY_STATE_ADDED,
    CATEGORY_STATE_DELETED,
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

    //Dummy
    DUMMY,

    //Mat state
    MAT_STATE_INITIALIZED,
    DASHBOARD_STATE_INITIALIZED,
    DASHBOARD_STATE_UPDATED,
    DASHBOARD_STATE_DELETED,
    PERIOD_INITIALIZED,
    DASHBOARD_PERIOD_DELETED,
    UNDISPATCHED_MAT_ADDED,
    MAT_DELETED,
    MAT_STATE_DELETED
}