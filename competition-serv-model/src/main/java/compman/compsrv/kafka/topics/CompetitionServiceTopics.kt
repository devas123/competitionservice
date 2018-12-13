package compman.compsrv.kafka.topics

object CompetitionServiceTopics {
    //Entity <Competition properties>
    const val COMPETITION_STATE_SNAPSHOTS_TOPIC_NAME = "competition-snapshots-changelog"
    const val COMPETITION_COMMANDS_TOPIC_NAME = "competitions-global-commands"
    const val COMPETITION_EVENTS_TOPIC_NAME = "competitions-global-events"

    //Entity <Category State>
    const val CATEGORY_STATE_CHANGELOG_TOPIC_NAME = "category-state-changelog"
    const val CATEGORY_COMMANDS_TOPIC_NAME = "categorystate-commands"
    const val CATEGORY_EVENTS_TOPIC_NAME = "categorystate-events"

    //Entity <Competition Progress>
    const val DASHBOARD_STATE_CHANGELOG_TOPIC_NAME = "dashboard-state-changelog"
    const val DASHBOARD_COMMANDS_TOPIC_NAME = "mat-global-commands"
    const val DASHBOARD_EVENTS_TOPIC_NAME = "mat-global-events"


    //Entity <Mat State>
    const val MAT_STATE_CHANGELOG_TOPIC_NAME = "mat-state-changelog"
    const val MAT_COMMANDS_TOPIC_NAME = "mat-state-commands"
    const val MAT_EVENTS_TOPIC_NAME = "mat-state-events"


    //Internal
    const val COMPETITION_INTERNAL_EVENTS_TOPIC_NAME = "competitions-internal-events"
    const val MAT_GLOBAL_INTERNAL_EVENTS_TOPIC_NAME = "mat-global-internal-events"
}