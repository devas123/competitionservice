package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, RegistrationInfoUpdatedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object RegistrationInfoUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: RegistrationInfoUpdatedEvent => apply[F](x)
  }

  private def apply[F[+_] : Monad : CompetitionUpdateOperations](event: RegistrationInfoUpdatedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto <- OptionT.fromOption[F](Option(payload.getRegistrationInfo))
      rawGroups = Option(dto.getRegistrationGroups).map(_.toList).getOrElse(List.empty)
      rawPeriods = Option(dto.getRegistrationPeriods).map(_.toList).getOrElse(List.empty)
      registrationGroups <- OptionT.liftF(rawGroups.traverse(DtoMapping.mapRegistrationGroup[F](competitionId)))
      registrationPeriods <- OptionT.liftF(rawPeriods.traverse(DtoMapping.mapRegistrationPeriod[F](competitionId)))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationGroups(registrationGroups))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationPeriods(registrationPeriods))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationOpen(competitionId))
    } yield ()
  }.value.map(_ => ())
}
