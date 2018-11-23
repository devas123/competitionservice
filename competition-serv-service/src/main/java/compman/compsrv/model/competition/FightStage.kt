package compman.compsrv.model.competition


enum class FightStage(val stage: String) {

    PENDING("pending"),
    GET_READY("get_ready"),
    IN_PROGRESS("in_progress"),
    PAUSED("paused"),
    FINISHED("finished")

}