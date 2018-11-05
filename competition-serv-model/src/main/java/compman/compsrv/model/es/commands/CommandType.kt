package compman.compsrv.model.es.commands

enum class CommandType(val scopes: List<CommandScope>) {
    CHECK_CATEGORY_OBSOLETE(listOf(CommandScope.COMPETITION)),
    CHANGE_COMPETITOR_CATEGORY_COMMAND(listOf(CommandScope.COMPETITION)),
    SAVE_SCHEDULE_COMMAND(listOf(CommandScope.COMPETITION)),
    SAVE_ABSOLUTE_CATEGORY_COMMAND(listOf(CommandScope.COMPETITION)),
    GENERATE_SCHEDULE_COMMAND(listOf(CommandScope.COMPETITION)),
    GENERATE_BRACKETS_COMMAND(listOf(CommandScope.COMPETITION)),
    UPDATE_CATEGORY_FIGHTS_COMMAND(listOf(CommandScope.CATEGORY)),
    DROP_SCHEDULE_COMMAND(listOf(CommandScope.COMPETITION)),
    DROP_ALL_BRACKETS_COMMAND(listOf(CommandScope.COMPETITION)),
    DROP_CATEGORY_BRACKETS_COMMAND(listOf(CommandScope.COMPETITION)),
    SAVE_BRACKETS_COMMAND(listOf(CommandScope.COMPETITION)),
    START_COMPETITION_COMMAND(listOf(CommandScope.COMPETITION)),
    STOP_COMPETITION_COMMAND(listOf(CommandScope.COMPETITION)),
    UPDATE_COMPETITION_PROPERTIES_COMMAND(listOf(CommandScope.COMPETITION)),
    CREATE_COMPETITION_COMMAND(listOf(CommandScope.COMPETITION)),
    PUBLISH_COMPETITION_COMMAND(listOf(CommandScope.COMPETITION)),
    UNPUBLISH_COMPETITION_COMMAND(listOf(CommandScope.COMPETITION)),
    DELETE_COMPETITION_COMMAND(listOf(CommandScope.COMPETITION)),
    ADD_CATEGORY_COMMAND(listOf(CommandScope.COMPETITION)),
    INIT_CATEGORY_STATE_COMMAND(listOf(CommandScope.CATEGORY)),
    DELETE_CATEGORY_STATE_COMMAND(listOf(CommandScope.CATEGORY)),
    DELETE_CATEGORY_COMMAND(listOf(CommandScope.COMPETITION)),
    CHANGE_COMPETITOR_FIGHT_COMMAND(listOf(CommandScope.COMPETITION)),

    CREATE_FAKE_COMPETITORS_COMMAND(listOf(CommandScope.CATEGORY)),

    //dummy command
    DUMMY_COMMAND(listOf(CommandScope.COMPETITION)),
    //mat state commands
    INIT_MAT_STATE_COMMAND(listOf(CommandScope.COMPETITION)),
    DELETE_MAT_STATE_COMMAND(listOf(CommandScope.COMPETITION)),
    INIT_PERIOD_COMMAND(listOf(CommandScope.COMPETITION)),
    DELETE_PERIOD_COMMAND(listOf(CommandScope.COMPETITION)),
    INIT_DASHBOARD_STATE_COMMAND(listOf(CommandScope.COMPETITION)),
    DELETE_DASHBOARD_STATE_COMMAND(listOf(CommandScope.COMPETITION)),
    ADD_UNDISPATCHED_MAT_COMMAND(listOf(CommandScope.COMPETITION)),

    CHECK_DASHBOARD_OBSOLETE(listOf(CommandScope.COMPETITION)),
    CHECK_MAT_OBSOLETE(listOf(CommandScope.COMPETITION))


}