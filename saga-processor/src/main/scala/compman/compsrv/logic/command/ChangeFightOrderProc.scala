package compman.compsrv.logic.command

import cats.{Monad, Traverse}
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{ChangeFightOrderCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.commands.payload.ChangeFightOrderPayload
import compman.compsrv.model.dto.competition.{FightDescriptionDTO, FightStatus}
import compman.compsrv.model.events.payload.{FightPropertiesUpdate, FightPropertiesUpdatedPayload}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.collection.mutable.ListBuffer

object ChangeFightOrderProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ ChangeFightOrderCommand(_, _, _) =>
      process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: ChangeFightOrderCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, NoPayloadError())
        fightToMove = state.fights.flatMap(_.get(payload.getFightId))
        newMatExists = state
          .schedule
          .exists(sched => sched.getMats.exists(mat => payload.getNewMatId == mat.getId))
        event <-
          if (fightToMove.isEmpty) {
            EitherT.fromEither(
              Left[Errors.Error, Seq[EventDTO]](Errors.FightDoesNotExist(payload.getFightId))
            )
          } else if (!newMatExists) {
            EitherT
              .fromEither(Left[Errors.Error, Seq[EventDTO]](Errors.MatDoesNotExist(payload.getNewMatId)))
          } else if (
            fightToMove
              .exists(f => Seq(FightStatus.IN_PROGRESS, FightStatus.FINISHED).contains(f.getStatus))
          ) {
            EitherT.fromEither(
              Left[Errors.Error, Seq[EventDTO]](Errors.FightCannotBeMoved(payload.getFightId))
            )
          } else {
            val updates = generateUpdates(payload, fightToMove.get, state.fights.get)
            val events =
              Traverse[Seq].traverse(updates)(u => {
                CommandEventOperations[F, EventDTO, EventType].create(
                  `type` = EventType.FIGHT_PROPERTIES_UPDATED,
                  competitorId = None,
                  competitionId = command.competitionId,
                  categoryId = Option(u._1),
                  payload = Some(new FightPropertiesUpdatedPayload().setUpdate(u._2))
                )
              })
            EitherT.liftF[F, Errors.Error, Seq[EventDTO]](events)
          }
      } yield event
    eventT.value
  }

  private def generateUpdates(
      payload: ChangeFightOrderPayload,
      fight: FightDescriptionDTO,
      fights: Map[String, FightDescriptionDTO]
  ) = {
    val newOrderOnMat                 = Math.max(payload.getNewOrderOnMat, 0)
    var startTime: Option[Instant]    = None
    var maxStartTime: Option[Instant] = None
    val currentMatId                  = fight.getMatId
    val currentNumberOnMat            = fight.getNumberOnMat
    val duration                      = fight.getDuration.longValue()
    val updates                       = ListBuffer.empty[(String, FightPropertiesUpdate)]

    def sameMatAsTargetFight(f: FightDescriptionDTO) = {
      f.getId != payload.getFightId && f.getMatId == currentMatId && f.getNumberOnMat != null &&
      f.getNumberOnMat >= currentNumberOnMat
    }

    def isOnNewMat(f: FightDescriptionDTO) = {
      f.getId != payload.getFightId && f.getMatId == payload.getNewMatId &&
      f.getNumberOnMat != null && f.getNumberOnMat >= payload.getNewOrderOnMat
    }

    def shouldUpdatePosition(f: FightDescriptionDTO) = {
      f.getId != payload.getFightId && f.getMatId == currentMatId && f.getNumberOnMat != null &&
      f.getNumberOnMat >= Math.min(currentNumberOnMat, payload.getNewOrderOnMat) &&
      f.getNumberOnMat <= Math.max(currentNumberOnMat, payload.getNewOrderOnMat)
    }

    if (payload.getNewMatId != fight.getMatId) {
      //if mats are different
      for (f <- fights.values) {
        val (ms, sm) = updateStartTimes(f, payload, startTime, maxStartTime, newOrderOnMat)
        maxStartTime = ms
        startTime = sm
        if (sameMatAsTargetFight(f)) {
          //first reduce numbers on the current mat
          updates.addOne(moveEarlier(duration, f))
        } else if (isOnNewMat(f)) {
          updates.addOne(moveLater(duration, f))
        }
      }
    } else {
      //mats are the same
      for (f <- fights.values) {
        val (ms, sm) = updateStartTimes(f, payload, startTime, maxStartTime, newOrderOnMat)
        maxStartTime = ms
        startTime = sm
        if (shouldUpdatePosition(f)) {
          //first reduce numbers on the current mat
          if (currentNumberOnMat > payload.getNewOrderOnMat) {
            updates.addOne(moveLater(duration, f))
          } else {
            //update fight
            updates.addOne(moveEarlier(duration, f))
          }
        }
      }
    }
    updates.addOne(
      (
        fight.getCategoryId,
        new FightPropertiesUpdate()
          .setFightId(fight.getId)
          .setMatId(payload.getNewMatId)
          .setStartTime(startTime.orElse(maxStartTime).orNull)
          .setNumberOnMat(newOrderOnMat)
      )
    )
    updates.toSeq
  }

  private def moveEarlier(duration: Long, f: FightDescriptionDTO) = {
    (
      f.getCategoryId,
      new FightPropertiesUpdate()
        .setFightId(f.getId)
        .setMatId(f.getMatId)
        .setNumberOnMat(f.getNumberOnMat - 1)
        .setStartTime(f.getStartTime.minus(duration, ChronoUnit.MINUTES))
    )
  }

  private def moveLater(duration: Long, f: FightDescriptionDTO) = {
    (
      f.getCategoryId,
      new FightPropertiesUpdate()
        .setFightId(f.getId)
        .setMatId(f.getMatId)
        .setNumberOnMat(f.getNumberOnMat + 1)
        .setStartTime(f.getStartTime.plus(duration, ChronoUnit.MINUTES))
    )
  }

  private def updateStartTimes(
      f: FightDescriptionDTO,
      payload: ChangeFightOrderPayload,
      startTime: Option[Instant],
      maxStartTime: Option[Instant],
      newOrderOnMat: Int
  ): (Option[Instant], Option[Instant]) = {
    var startTime1    = startTime
    var maxStartTime1 = maxStartTime
    if (
      f.getId != payload.getFightId && f.getMatId == payload.getNewMatId &&
      f.getNumberOnMat == newOrderOnMat
    ) {
      startTime1 = Option(f.getStartTime)
    }
    if (
      f.getId != payload.getFightId && f.getMatId == payload.getNewMatId &&
      !maxStartTime1.exists(_.isAfter(f.getStartTime))
    ) {
      maxStartTime1 = Option(f.getStartTime)
    }
    (maxStartTime1, startTime1)
  }

}
