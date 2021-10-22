package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, ScheduleDropped}
import compman.compsrv.query.model.FightStartTimeUpdate
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object ScheduleDroppedProc {
  def apply[F[+_]: CompetitionQueryOperations: Monad: CompetitionUpdateOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: ScheduleDropped => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: ScheduleDropped
  ): F[Unit] = {
    for {
      competitionId <- OptionT.fromOption[F](event.competitionId)
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].removePeriods(competitionId))
      fights        <- OptionT.liftF(CompetitionQueryOperations[F].getFightsByScheduleEntries(competitionId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateFightStartTime(fights.map(f =>
        FightStartTimeUpdate(
          id = f.fightId,
          competitionId = f.competitionId,
          categoryId = f.categoryId,
          matId = None,
          matName = None,
          matOrder = None,
          numberOnMat = None,
          startTime = None,
          invalid = None,
          scheduleEntryId = None,
          periodId = None,
          priority = None
        )
      )))
    } yield ()
  }.value.map(_ => ())
}
