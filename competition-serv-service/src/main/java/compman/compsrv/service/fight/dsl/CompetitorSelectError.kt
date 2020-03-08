package compman.compsrv.service.fight.dsl

import compman.compsrv.model.dto.brackets.StageResultDescriptorDTO

sealed class CompetitorSelectError {
    data class NotEnoughCompetitors(val results: StageResultDescriptorDTO): CompetitorSelectError()
    data class NoCompetitorsSelected(val results: StageResultDescriptorDTO): CompetitorSelectError()
    data class UnknownError(val errorMsg: String, val cause: Throwable?): CompetitorSelectError()
    data class NoWinnerOfFight(val id: String, val cause: Throwable?): CompetitorSelectError()
}
