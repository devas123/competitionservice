package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, RegistrationPeriodAddedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object RegistrationPeriodAddedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: RegistrationPeriodAddedEvent => apply[F](x)
  }

  private def apply[F[+_] : Monad : CompetitionUpdateOperations](event: RegistrationPeriodAddedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto <- OptionT.fromOption[F](payload.period)
      mapped = DtoMapping.mapRegistrationPeriod(competitionId)(dto)
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationPeriod(mapped))
    } yield ()
  }.value.map(_ => ())
}
