package compman.compsrv.model.commands;

public enum CommandType {
    CHECK_CATEGORY_OBSOLETE,
    CHANGE_COMPETITOR_CATEGORY_COMMAND,
    SAVE_SCHEDULE_COMMAND,
    SAVE_ABSOLUTE_CATEGORY_COMMAND,
    GENERATE_SCHEDULE_COMMAND,
    GENERATE_BRACKETS_COMMAND,
    UPDATE_CATEGORY_FIGHTS_COMMAND,
    DROP_SCHEDULE_COMMAND,
    DROP_ALL_BRACKETS_COMMAND,
    DROP_CATEGORY_BRACKETS_COMMAND,
    SAVE_BRACKETS_COMMAND,
    START_COMPETITION_COMMAND,
    STOP_COMPETITION_COMMAND,
    UPDATE_COMPETITION_PROPERTIES_COMMAND,
    CREATE_COMPETITION_COMMAND,
    PUBLISH_COMPETITION_COMMAND,
    UNPUBLISH_COMPETITION_COMMAND,
    DELETE_COMPETITION_COMMAND,
    ADD_CATEGORY_COMMAND,
    DELETE_CATEGORY_STATE_COMMAND,
    DELETE_CATEGORY_COMMAND,
    CHANGE_COMPETITOR_FIGHT_COMMAND,
    ADD_REGISTRATION_PERIOD_COMMAND,
    ADD_REGISTRATION_GROUP_COMMAND,
    DELETE_REGISTRATION_GROUP_COMMAND,
    DELETE_REGISTRATION_PERIOD_COMMAND,

    //dummy command
    DUMMY_COMMAND,

    //mat state commands
    INIT_MAT_STATE_COMMAND,
    DELETE_MAT_STATE_COMMAND,
    INIT_PERIOD_COMMAND,
    DELETE_PERIOD_COMMAND,
    INIT_DASHBOARD_STATE_COMMAND,
    DELETE_DASHBOARD_STATE_COMMAND,
    ADD_UNDISPATCHED_MAT_COMMAND,
    CHECK_DASHBOARD_OBSOLETE,

    CHECK_MAT_OBSOLETE,
    SET_CATEGORY_BRACKETS_COMMAND,

    PUBLISH_EVENTS_COMMAND,

    ADD_COMPETITOR_COMMAND,
    CREATE_FAKE_COMPETITORS_COMMAND,
    UPDATE_COMPETITOR_COMMAND,
    REMOVE_COMPETITOR_COMMAND
}
