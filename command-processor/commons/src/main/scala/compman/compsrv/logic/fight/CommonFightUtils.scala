package compman.compsrv.logic.fight

import compman.compsrv.model.commands.payload.ChangeFightOrderPayload
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.events.payload.FightOrderUpdate

import java.time.temporal.ChronoUnit
import java.time.Instant
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
    startTime: Instant
  )

  implicit def asMatView(mat: MatDescriptionDTO): MatView =
    MatView(mat.getId, Option(mat.getName), mat.getPeriodId, mat.getMatOrder)
  implicit def asFightView(fight: FightDescriptionDTO): FightView = FightView(
    fight.getId,
    fight.getMat,
    fight.getNumberOnMat,
    fight.getDuration.longValue(),
    fight.getCategoryId,
    fight.getStartTime
  )

  implicit def asFightViews(fights: Map[String, FightDescriptionDTO]): Map[String, FightView] = fights.view
    .mapValues(asFightView).toMap

  def generateUpdates(
    payload: ChangeFightOrderPayload,
    fight: FightView,
    fights: Map[String, FightView]
  ): Seq[(String, FightOrderUpdate)] = {
    val newOrderOnMat                 = Math.max(payload.getNewOrderOnMat, 0)
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
      f.id != payload.getFightId && matsEqual(f.mat, currentMat) && f.numberOnMat >= currentNumberOnMat
    }

    def isOnNewMat(f: FightView) = {
      f.id != payload.getFightId && fightMatIdMatchesNewMatId(f, payload) && f.numberOnMat >= payload.getNewOrderOnMat
    }

    def shouldUpdatePosition(f: FightView) = {
      f.id != payload.getFightId && matsEqual(f.mat, currentMat) &&
      f.numberOnMat >= Math.min(currentNumberOnMat, payload.getNewOrderOnMat) &&
      f.numberOnMat <= Math.max(currentNumberOnMat, payload.getNewOrderOnMat)
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
          if (currentNumberOnMat > payload.getNewOrderOnMat) { updates.addOne(moveLater(duration, f)) }
          else {
            //update fight
            updates.addOne(moveEarlier(duration, f))
          }
        }
      }
    }
    updates.addOne((
      fight.categoryId,
      new FightOrderUpdate().setFightId(fight.id).setMatId(payload.getNewMatId)
        .setStartTime(startTime.orElse(maxStartTime).orNull).setNumberOnMat(newOrderOnMat)
    ))
    updates.toSeq
  }

  private def moveEarlier(duration: Long, f: FightView) = {
    (f.categoryId, createUpdate(f, f.numberOnMat - 1, f.startTime.minus(duration, ChronoUnit.MINUTES)))
  }

  private def createUpdate(f: FightView, newNumberOnMat: Int, newStarTime: Instant) = {
    new FightOrderUpdate().setFightId(f.id).setMatId(Option(f.mat).flatMap(m => Option(m.id)).orNull)
      .setNumberOnMat(newNumberOnMat).setStartTime(newStarTime)
  }

  private def moveLater(duration: Long, f: FightView) = {
    (f.categoryId, createUpdate(f, f.numberOnMat + 1, f.startTime.plus(duration, ChronoUnit.MINUTES)))
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
    if (f.id != payload.getFightId && fightMatIdMatchesNewMatId(f, payload) && f.numberOnMat == newOrderOnMat) {
      startTime1 = Option(f.startTime)
    }
    if (
      f.id != payload.getFightId && fightMatIdMatchesNewMatId(f, payload) &&
      !maxStartTime1.exists(_.isAfter(f.startTime))
    ) { maxStartTime1 = Option(f.startTime) }
    (maxStartTime1, startTime1)
  }

  private def fightMatIdMatchesNewMatId(f: FightView, payload: ChangeFightOrderPayload) = {
    Option(f.mat).flatMap(m => Option(m.id)).contains(payload.getNewMatId)
  }

}
