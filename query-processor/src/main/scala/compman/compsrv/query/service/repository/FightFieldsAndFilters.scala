package compman.compsrv.query.service.repository

import compservice.model.protobuf.model.FightStatus
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{equal, or}
import org.mongodb.scala.model.Indexes

trait FightFieldsAndFilters extends CommonFields {


  val scores      = "scores"
  val fightResult = "fightResult"
  val status      = "status"
  val bracketsInfo      = "bracketsInfo"
  val round      = "round"
  val numberInRound      = "numberInRound"
  val statusCheck: Bson = or(
    equal(status, FightStatus.PENDING),
    equal(status, FightStatus.GET_READY),
    equal(status, FightStatus.IN_PROGRESS),
    equal(status, FightStatus.PAUSED)
  )

  val fightsCollectionIndex: Bson = Indexes.ascending(competitionIdField, s"$bracketsInfo.$round", s"$bracketsInfo.$numberInRound")
}
