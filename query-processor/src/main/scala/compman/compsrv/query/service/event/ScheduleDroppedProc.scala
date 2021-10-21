package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, ScheduleDropped}
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object ScheduleDroppedProc {
  def apply[F[+_]: Monad: CompetitionUpdateOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: ScheduleDropped => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: ScheduleDropped): F[Unit] = {
    for {
      competitionId <- OptionT.fromOption[F](event.competitionId)
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].removePeriods(competitionId))
    } yield ()
  }.value.map(_ => ())
}
