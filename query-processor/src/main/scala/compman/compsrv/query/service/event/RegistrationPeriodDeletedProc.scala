package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, RegistrationPeriodDeletedEvent}
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object RegistrationPeriodDeletedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: RegistrationPeriodDeletedEvent => apply[F](x)
  }

  private def apply[F[+_] : Monad : CompetitionUpdateOperations](event: RegistrationPeriodDeletedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      periodId <- OptionT.fromOption[F](Option(payload.periodId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeRegistrationPeriod(competitionId)(periodId))
    } yield ()
  }.value.map(_ => ())
}
