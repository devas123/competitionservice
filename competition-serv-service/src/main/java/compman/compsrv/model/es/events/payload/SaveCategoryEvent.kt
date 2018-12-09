package compman.compsrv.model.es.events.payload

import compman.compsrv.model.competition.FightDescription


class SaveCategoryEvent {
    var categoryId: String? = ""
    var fights: Array<FightDescription>? = emptyArray()
}