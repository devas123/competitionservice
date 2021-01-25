package compman.compsrv.model.commands;

import compman.compsrv.model.commands.payload.*;

public enum CommandType {
    CHECK_CATEGORY_OBSOLETE(null),
    CHANGE_COMPETITOR_CATEGORY_COMMAND(ChangeCompetitorCategoryPayload.class),
    SAVE_SCHEDULE_COMMAND(null),
    SAVE_ABSOLUTE_CATEGORY_COMMAND(GenerateAbsoluteCategoryPayload.class),
    GENERATE_SCHEDULE_COMMAND(GenerateSchedulePayload.class),
    GENERATE_BRACKETS_COMMAND(GenerateBracketsPayload.class),
    UPDATE_CATEGORY_FIGHTS_COMMAND(null),
    DROP_SCHEDULE_COMMAND(null),
    DROP_ALL_BRACKETS_COMMAND(null),
    DROP_CATEGORY_BRACKETS_COMMAND(null),
    SAVE_BRACKETS_COMMAND(null),
    START_COMPETITION_COMMAND(null),
    STOP_COMPETITION_COMMAND(null),
    UPDATE_COMPETITION_PROPERTIES_COMMAND(null),
    CREATE_COMPETITION_COMMAND(CreateCompetitionPayload.class),
    PUBLISH_COMPETITION_COMMAND(null),
    UNPUBLISH_COMPETITION_COMMAND(null),
    DELETE_COMPETITION_COMMAND(null),
    ADD_CATEGORY_COMMAND(AddCategoryPayload.class),
    GENERATE_CATEGORIES_COMMAND(GenerateCategoriesFromRestrictionsPayload.class),
    DELETE_CATEGORY_COMMAND(null),
    FIGHTS_EDITOR_APPLY_CHANGE(FightEditorApplyChangesPayload.class),
    ADD_REGISTRATION_PERIOD_COMMAND(AddRegistrationPeriodPayload.class),
    ADD_REGISTRATION_GROUP_COMMAND(AddRegistrationGroupPayload.class),
    ADD_REGISTRATION_GROUP_TO_REGISTRATION_PERIOD_COMMAND(RegistrationPeriodAddRegistrationGroupPayload.class),
    DELETE_REGISTRATION_GROUP_COMMAND(DeleteRegistrationGroupPayload.class),
    DELETE_REGISTRATION_PERIOD_COMMAND(DeleteRegistrationPeriodPayload.class),
    ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND(AssignRegistrationGroupCategoriesPayload.class),
    UPDATE_REGISTRATION_INFO_COMMAND(UpdateRegistrationInfoPayload.class),
    UPDATE_STAGE_STATUS_COMMAND(UpdateStageStatusPayload.class),
    CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND(CategoryRegistrationStatusChangePayload.class),

    //dummy command
    DUMMY_COMMAND(null),
    INTERNAL_SEND_PROCESSING_INFO_COMMAND(null),

    //mat state commands
    INIT_MAT_STATE_COMMAND(null),
    DELETE_MAT_STATE_COMMAND(null),
    INIT_PERIOD_COMMAND(null),
    DELETE_PERIOD_COMMAND(null),
    DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND(DashboardFightOrderChangePayload.class),
    DASHBOARD_SET_FIGHT_RESULT_COMMAND(SetFightResultPayload.class),
    ADD_UNDISPATCHED_MAT_COMMAND(null),
    CHECK_DASHBOARD_OBSOLETE(null),
    CHECK_MAT_OBSOLETE(null),
    SET_CATEGORY_BRACKETS_COMMAND(null),
    ADD_COMPETITOR_COMMAND(AddCompetitorPayload.class),
    CREATE_FAKE_COMPETITORS_COMMAND(CreateFakeCompetitorsPayload.class),
    UPDATE_COMPETITOR_COMMAND(UpdateCompetitorPayload.class),
    REMOVE_COMPETITOR_COMMAND(RemoveCompetitorPayload.class),
    PROPAGATE_COMPETITORS_COMMAND(PropagateCompetitorsPayload.class);

    private final Class<? extends Payload> payloadClass;

    CommandType(Class<? extends Payload> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<? extends Payload> getPayloadClass() {
        return payloadClass;
    }
}
