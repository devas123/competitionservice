package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.Utils
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightStartTimeUpdatedEvent}
import compman.compsrv.query.model.FightStartTimeUpdate
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightQueryOperations, FightUpdateOperations}

import java.util.Date

object FightStartTimeUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations: FightQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightStartTimeUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations: FightQueryOperations](
    event: FightStartTimeUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      updates       <- OptionT.fromOption[F](Option(payload.getNewFights))
      existing <- updates.groupBy(_.getFightCategoryId).toList.traverse(arr =>
        OptionT.liftF(FightQueryOperations[F].getFightsByIds(competitionId)(arr._1, arr._2.map(_.getFightId).toSet))
      )
      updatesMap = Utils.groupById(updates)(_.getFightId)
      periods <- OptionT.liftF(CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId))
      mats = Utils.groupById(periods.flatMap(_.mats))(_.matId)
      existingUpdated = existing.flatten.map { f =>
        val u   = updatesMap(f.id)
        val mat = Option(u.getMatId).flatMap(mats.get)
        f.copy(
          scheduleEntryId = Option(u.getScheduleEntryId),
          matId = mat.map(_.matId),
          matName = mat.map(_.name),
          matOrder = mat.map(_.matOrder),
          numberOnMat = Option(u.getNumberOnMat),
          periodId = Option(u.getPeriodId),
          startTime = Option(u.getStartTime).map(Date.from),
          invalid = Option(u.getInvalid)
        )
      }
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFightStartTime(existingUpdated.map(f =>
        FightStartTimeUpdate(
          id = f.id,
          competitionId = f.competitionId,
          categoryId = f.categoryId,
          matId = f.matId,
          matName = f.matName,
          matOrder = f.matOrder,
          numberOnMat = f.numberOnMat,
          startTime = f.startTime,
          invalid = f.invalid,
          scheduleEntryId = f.scheduleEntryId,
          periodId = f.periodId,
          priority = f.priority
        )
      )))
    } yield ()
  }.value.map(_ => ())
}
