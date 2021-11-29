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
      updatesMap = Utils.groupById(updates)(_.getFightId)
      periods <- OptionT.liftF(CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId))
      mats = Utils.groupById(periods.flatMap(_.mats))(_.matId)
      existingUpdated = updatesMap.map { case (id, f) =>
        val u   = updatesMap(f.getFightId)
        val mat = Option(u.getMatId).flatMap(mats.get)
        FightStartTimeUpdate(
          id = id,
          scheduleEntryId = Option(u.getScheduleEntryId),
          matId = mat.map(_.matId),
          matName = mat.map(_.name),
          matOrder = mat.map(_.matOrder),
          numberOnMat = Option(u.getNumberOnMat),
          periodId = Option(u.getPeriodId),
          startTime = Option(u.getStartTime).map(Date.from),
          invalid = Option(u.getInvalid),
          competitionId = competitionId,
          categoryId = f.getFightCategoryId,
          priority = None
        )
      }.toList
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFightStartTime(existingUpdated))
    } yield ()
  }.value.map(_ => ())
}
