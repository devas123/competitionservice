package compman.compsrv.kafka.topics

object CompetitionServiceTopics {
    const val COMPETITION_STATE_CHANGELOG_TOPIC_NAME = "competition-properties-changelog"
    const val CATEGORY_STATE_CHANGELOG_TOPIC_NAME = "categorystate-changelog"
    const val MAT_STATE_CHANGELOG_TOPIC_NAME = "mat-state-changelog"
    const val DASHBOARD_STATE_CHANGELOG_TOPIC_NAME = "dashboard-state-changelog"
    const val CATEGORIES_COMMANDS_TOPIC_NAME = "categorystate-commands"
    const val COMPETITIONS_COMMANDS_TOPIC_NAME = "competitions-global-commands"
    const val COMPETITIONS_INTERNAL_EVENTS_TOPIC_NAME = "competitions-internal-events"
    const val COMPETITIONS_EVENTS_TOPIC_NAME = "competitions-global-events"
    const val CATEGORIES_EVENTS_TOPIC_NAME = "categorystate-events"
    const val MATS_COMMANDS_TOPIC_NAME = "mat-state-commands"
    const val MATS_GLOBAL_COMMANDS_TOPIC_NAME = "mat-global-commands"
    const val MATS_EVENTS_TOPIC_NAME = "mat-state-events"
    const val MATS_GLOBAL_EVENTS_TOPIC_NAME = "mat-global-events"
    const val MATS_GLOBAL_INTERNAL_EVENTS_TOPIC_NAME = "mat-global-internal-events"
}