package compman.compsrv.query.service.repository

import compman.compsrv.logic.fight.FightStatusUtils
import compservice.model.protobuf.model.FightStatus
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{equal, not, or}
import org.mongodb.scala.model.Indexes

trait FightFieldsAndFilters extends CommonFields {


  val scores      = "scores"
  val fightResult = "fightResult"
  val status      = "status"
  val bracketsInfo      = "bracketsInfo"
  val round      = "round"
  val numberInRound      = "numberInRound"
  val activeFight: Bson = or(
    FightStatusUtils.activeFightStatus
      .map(st => equal(status, st)).toIndexedSeq: _*
  )

  val completableFightsStatuses: Bson = not(
    equal(status, FightStatus.UNCOMPLETABLE),
  )

  val fightsCollectionIndex: Bson = Indexes.ascending(competitionIdField, s"$bracketsInfo.$round", s"$bracketsInfo.$numberInRound")
}
