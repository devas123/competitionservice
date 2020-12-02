package compman.compsrv.aggregate

import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventType

object AggregateTypeDecider {
    fun getCommandAggregateType(commandType: CommandType): AggregateType {
        return when(commandType) {
            CommandType.CHECK_CATEGORY_OBSOLETE -> AggregateType.CATEGORY
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> AggregateType.SAGA
            CommandType.SAVE_SCHEDULE_COMMAND -> AggregateType.COMPETITION
            CommandType.SAVE_ABSOLUTE_CATEGORY_COMMAND -> AggregateType.CATEGORY
            CommandType.GENERATE_SCHEDULE_COMMAND -> AggregateType.COMPETITION
            CommandType.GENERATE_BRACKETS_COMMAND -> AggregateType.CATEGORY
            CommandType.UPDATE_CATEGORY_FIGHTS_COMMAND -> AggregateType.CATEGORY
            CommandType.DROP_SCHEDULE_COMMAND -> AggregateType.COMPETITION
            CommandType.DROP_ALL_BRACKETS_COMMAND -> AggregateType.COMPETITION
            CommandType.DROP_CATEGORY_BRACKETS_COMMAND -> AggregateType.CATEGORY
            CommandType.SAVE_BRACKETS_COMMAND -> AggregateType.CATEGORY
            CommandType.START_COMPETITION_COMMAND -> AggregateType.COMPETITION
            CommandType.STOP_COMPETITION_COMMAND -> AggregateType.COMPETITION
            CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND -> AggregateType.COMPETITION
            CommandType.CREATE_COMPETITION_COMMAND -> AggregateType.COMPETITION
            CommandType.PUBLISH_COMPETITION_COMMAND -> AggregateType.COMPETITION
            CommandType.UNPUBLISH_COMPETITION_COMMAND -> AggregateType.COMPETITION
            CommandType.DELETE_COMPETITION_COMMAND -> AggregateType.COMPETITION
            CommandType.ADD_CATEGORY_COMMAND -> AggregateType.CATEGORY
            CommandType.GENERATE_CATEGORIES_COMMAND -> AggregateType.CATEGORY
            CommandType.DELETE_CATEGORY_COMMAND -> AggregateType.CATEGORY
            CommandType.FIGHTS_EDITOR_APPLY_CHANGE -> AggregateType.CATEGORY
            CommandType.ADD_REGISTRATION_PERIOD_COMMAND -> AggregateType.COMPETITION
            CommandType.ADD_REGISTRATION_GROUP_COMMAND -> AggregateType.COMPETITION
            CommandType.ADD_REGISTRATION_GROUP_TO_REGISTRATION_PERIOD_COMMAND -> AggregateType.COMPETITION
            CommandType.DELETE_REGISTRATION_GROUP_COMMAND -> AggregateType.COMPETITION
            CommandType.DELETE_REGISTRATION_PERIOD_COMMAND -> AggregateType.COMPETITION
            CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND -> AggregateType.COMPETITION
            CommandType.UPDATE_REGISTRATION_INFO_COMMAND -> AggregateType.COMPETITION
            CommandType.UPDATE_STAGE_STATUS_COMMAND -> AggregateType.CATEGORY
            CommandType.CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND -> AggregateType.CATEGORY
            CommandType.DUMMY_COMMAND -> AggregateType.SAGA
            CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND -> AggregateType.SAGA
            CommandType.INIT_MAT_STATE_COMMAND -> AggregateType.COMPETITION
            CommandType.DELETE_MAT_STATE_COMMAND -> AggregateType.COMPETITION
            CommandType.INIT_PERIOD_COMMAND -> AggregateType.COMPETITION
            CommandType.DELETE_PERIOD_COMMAND -> AggregateType.COMPETITION
            CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND -> AggregateType.CATEGORY
            CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND -> AggregateType.CATEGORY
            CommandType.ADD_UNDISPATCHED_MAT_COMMAND -> AggregateType.COMPETITION
            CommandType.CHECK_DASHBOARD_OBSOLETE -> AggregateType.COMPETITION
            CommandType.CHECK_MAT_OBSOLETE -> AggregateType.COMPETITION
            CommandType.SET_CATEGORY_BRACKETS_COMMAND -> AggregateType.CATEGORY
            CommandType.ADD_COMPETITOR_COMMAND -> AggregateType.COMPETITOR
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> AggregateType.COMPETITOR
            CommandType.UPDATE_COMPETITOR_COMMAND -> AggregateType.COMPETITOR
            CommandType.REMOVE_COMPETITOR_COMMAND -> AggregateType.COMPETITOR
            CommandType.PROPAGATE_COMPETITORS_COMMAND -> AggregateType.CATEGORY
        }
    }

    fun getEventAggregateType(eventType: EventType): AggregateType {
        return when(eventType) {
            EventType.ERROR_EVENT -> AggregateType.SAGA
            EventType.BRACKETS_GENERATED -> AggregateType.CATEGORY
            EventType.SCHEDULE_GENERATED -> AggregateType.COMPETITION
            EventType.FIGHTS_ADDED_TO_STAGE -> AggregateType.CATEGORY
            EventType.STAGE_STATUS_UPDATED -> AggregateType.CATEGORY
            EventType.COMPETITOR_ADDED -> AggregateType.COMPETITOR
            EventType.COMPETITOR_REMOVED -> AggregateType.COMPETITOR
            EventType.COMPETITOR_UPDATED -> AggregateType.COMPETITOR
            EventType.COMPETITOR_CATEGORY_CHANGED -> AggregateType.COMPETITOR
            EventType.CATEGORY_ADDED -> AggregateType.CATEGORY
            EventType.CATEGORY_DELETED -> AggregateType.CATEGORY
            EventType.CATEGORY_BRACKETS_DROPPED -> AggregateType.CATEGORY
            EventType.CATEGORY_REGISTRATION_STATUS_CHANGED -> AggregateType.CATEGORY
            EventType.COMPETITION_STARTED -> AggregateType.COMPETITION
            EventType.COMPETITION_CREATED -> AggregateType.COMPETITION
            EventType.COMPETITION_DELETED -> AggregateType.COMPETITION
            EventType.COMPETITION_PUBLISHED -> AggregateType.COMPETITION
            EventType.COMPETITION_UNPUBLISHED -> AggregateType.COMPETITION
            EventType.COMPETITION_STOPPED -> AggregateType.COMPETITION
            EventType.COMPETITION_PROPERTIES_UPDATED -> AggregateType.COMPETITION
            EventType.COMPETITORS_PROPAGATED_TO_STAGE -> AggregateType.CATEGORY
            EventType.FIGHTS_START_TIME_UPDATED -> AggregateType.CATEGORY
            EventType.FIGHTS_EDITOR_CHANGE_APPLIED -> AggregateType.CATEGORY
            EventType.SCHEDULE_DROPPED -> AggregateType.COMPETITION
            EventType.REGISTRATION_PERIOD_ADDED -> AggregateType.COMPETITION
            EventType.REGISTRATION_INFO_UPDATED -> AggregateType.COMPETITION
            EventType.REGISTRATION_PERIOD_DELETED -> AggregateType.COMPETITION
            EventType.REGISTRATION_GROUP_ADDED -> AggregateType.COMPETITION
            EventType.REGISTRATION_GROUP_ADDED_TO_REGISTRATION_PERIOD -> AggregateType.COMPETITION
            EventType.REGISTRATION_GROUP_DELETED -> AggregateType.COMPETITION
            EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED -> AggregateType.COMPETITION
            EventType.DASHBOARD_FIGHT_RESULT_SET -> AggregateType.CATEGORY
            EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED -> AggregateType.CATEGORY
            EventType.DASHBOARD_FIGHT_ORDER_CHANGED -> AggregateType.SAGA
            EventType.DASHBOARD_STAGE_RESULT_SET -> AggregateType.CATEGORY
            EventType.DUMMY -> AggregateType.SAGA
            EventType.INTERNAL_COMPETITION_INFO -> AggregateType.SAGA
        }
    }
}