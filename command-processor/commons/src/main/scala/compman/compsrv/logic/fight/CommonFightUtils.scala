package compman.compsrv.logic.fight

import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.timestamp.Timestamp.toJavaProto
import com.google.protobuf.util.Timestamps
import compservice.model.protobuf.commandpayload.ChangeFightOrderPayload
import compservice.model.protobuf.eventpayload.FightOrderUpdate
import compservice.model.protobuf.model.{FightDescription, MatDescription}

import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.Date
import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

object CommonFightUtils {
  case class MatView(id: String, name: Option[String], periodId: String, order: Int)
  case class FightView(
    id: String,
    mat: MatView,
    numberOnMat: Int,
    duration: Long,
    categoryId: String,
    startTime: Option[Instant]
  )

  implicit def asMatView(mat: MatDescription): MatView = MatView(mat.id, Option(mat.name), mat.periodId, mat.matOrder)
  implicit def asFightView(fight: FightDescription): FightView = FightView(
    fight.id,
    fight.mat.map(asMatView).orNull,
    fight.numberOnMat.getOrElse(0),
    fight.duration.longValue(),
    fight.categoryId,
    fight.startTime.map(st => Instant.ofEpochMilli(Timestamps.toMillis(toJavaProto(st))))
  )

  implicit def asFightViews(fights: Map[String, FightDescription]): Map[String, FightView] = fights.view
    .mapValues(asFightView).toMap

  def generateUpdates(
    payload: ChangeFightOrderPayload,
    fight: FightView,
    fights: Map[String, FightView]
  ): Seq[(String, FightOrderUpdate)] = {
    val newOrderOnMat                 = Math.max(payload.newOrderOnMat, 0)
    var startTime: Option[Instant]    = None
    var maxStartTime: Option[Instant] = None
    val currentMat                    = fight.mat
    val currentNumberOnMat            = fight.numberOnMat
    val duration                      = fight.duration.longValue()
    val updates                       = ListBuffer.empty[(String, FightOrderUpdate)]

    def matsEqual(m1: MatView, m2: MatView): Boolean = {
      for {
        f <- Option(m1)
        s <- Option(m2)
      } yield f.id == s.id
    }.getOrElse(m1 == null && m2 == null)

    def sameMatAsTargetFight(f: FightView) = {
      f.id != payload.fightId && matsEqual(f.mat, currentMat) && f.numberOnMat >= currentNumberOnMat
    }

    def isOnNewMat(f: FightView) = {
      f.id != payload.fightId && fightMatIdMatchesNewMatId(f, payload) && f.numberOnMat >= payload.newOrderOnMat
    }

    def shouldUpdatePosition(f: FightView) = {
      f.id != payload.fightId && matsEqual(f.mat, currentMat) &&
      f.numberOnMat >= Math.min(currentNumberOnMat, payload.newOrderOnMat) &&
      f.numberOnMat <= Math.max(currentNumberOnMat, payload.newOrderOnMat)
    }

    if (!fightMatIdMatchesNewMatId(fight, payload)) {
      //if mats are different
      for (f <- fights.values) {
        val (ms, sm) = updateStartTimes(f, payload, startTime, maxStartTime, newOrderOnMat)
        maxStartTime = ms
        startTime = sm
        if (sameMatAsTargetFight(f)) {
          //first reduce numbers on the current mat
          updates.addOne(moveEarlier(duration, f))
        } else if (isOnNewMat(f)) { updates.addOne(moveLater(duration, f)) }
      }
    } else {
      //mats are the same
      for (f <- fights.values) {
        val (ms, sm) = updateStartTimes(f, payload, startTime, maxStartTime, newOrderOnMat)
        maxStartTime = ms
        startTime = sm
        if (shouldUpdatePosition(f)) {
          //first reduce numbers on the current mat
          if (currentNumberOnMat > payload.newOrderOnMat) { updates.addOne(moveLater(duration, f)) }
          else {
            //update fight
            updates.addOne(moveEarlier(duration, f))
          }
        }
      }
    }
    updates.addOne((
      fight.categoryId,
      FightOrderUpdate().withFightId(fight.id).withMatId(payload.newMatId).withStartTime(
        startTime.orElse(maxStartTime).map(Date.from).map(Timestamps.fromDate).map(Timestamp.fromJavaProto).orNull
      ).withNumberOnMat(newOrderOnMat)
    ))
    updates.toSeq
  }

  private def moveEarlier(duration: Long, f: FightView) = {
    (f.categoryId, createUpdate(f, f.numberOnMat - 1, f.startTime.map(_.minus(duration, ChronoUnit.MINUTES))))
  }

  private def createUpdate(f: FightView, newNumberOnMat: Int, newStarTime: Option[Instant]) = {
    FightOrderUpdate().withFightId(f.id).withMatId(Option(f.mat).flatMap(m => Option(m.id)).orNull)
      .withNumberOnMat(newNumberOnMat).update(_.startTime.setIfDefined(newStarTime.map(st =>
        Timestamp.fromJavaProto(Timestamps.fromDate(Date.from(st)))
      )))
  }

  private def moveLater(duration: Long, f: FightView) = {
    (f.categoryId, createUpdate(f, f.numberOnMat + 1, f.startTime.map(_.plus(duration, ChronoUnit.MINUTES))))
  }

  private def updateStartTimes(
    f: FightView,
    payload: ChangeFightOrderPayload,
    startTime: Option[Instant],
    maxStartTime: Option[Instant],
    newOrderOnMat: Int
  ): (Option[Instant], Option[Instant]) = {
    var startTime1    = startTime
    var maxStartTime1 = maxStartTime
    if (f.id != payload.fightId && fightMatIdMatchesNewMatId(f, payload) && f.numberOnMat == newOrderOnMat) {
      startTime1 = f.startTime
    }
    if (
      f.id != payload.fightId && fightMatIdMatchesNewMatId(f, payload) &&
      !maxStartTime1.exists(maxStartTime => f.startTime.exists(maxStartTime.isAfter))
    ) { maxStartTime1 = f.startTime }
    (maxStartTime1, startTime1)
  }

  private def fightMatIdMatchesNewMatId(f: FightView, payload: ChangeFightOrderPayload) = {
    Option(f.mat).flatMap(m => Option(m.id)).contains(payload.newMatId)
  }

}
