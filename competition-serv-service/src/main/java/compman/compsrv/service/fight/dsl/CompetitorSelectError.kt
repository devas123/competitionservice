package compman.compsrv.service.fight.dsl

sealed class CompetitorSelectError {
    data class NoCompetitorsSelected(val ids: Collection<String>?): CompetitorSelectError()
    data class UnknownError(val errorMsg: String, val cause: Throwable?): CompetitorSelectError()
    data class NoWinnerOfFight(val id: String, val cause: Throwable?): CompetitorSelectError()
    data class NoLoserOfFight(val id: String, val cause: Throwable?): CompetitorSelectError()
    data class SelectedSizeNotMatch(val expected: Int, val actual: Int): CompetitorSelectError()
}
