package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.competition.FightStatus
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{equal, or}

trait FightFieldsAndFilters {

  val scores      = "scores"
  val fightResult = "fightResult"
  val status      = "status"
  val statusCheck: Bson = or(
    equal(status, FightStatus.PENDING),
    equal(status, FightStatus.GET_READY),
    equal(status, FightStatus.IN_PROGRESS),
    equal(status, FightStatus.PAUSED)
  )
}
