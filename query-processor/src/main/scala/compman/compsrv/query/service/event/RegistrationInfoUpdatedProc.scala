package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, RegistrationInfoUpdatedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

import scala.jdk.CollectionConverters._

object RegistrationInfoUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: RegistrationInfoUpdatedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: RegistrationInfoUpdatedEvent): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto           <- OptionT.fromOption[F](Option(payload.getRegistrationInfo))
      rawGroups           = Option(dto.getRegistrationGroups).map(_.asScala.values.toList).getOrElse(List.empty)
      rawPeriods          = Option(dto.getRegistrationPeriods).map(_.asScala.values.toList).getOrElse(List.empty)
      regOpen             = Option(dto.getRegistrationOpen).exists(_.booleanValue())
      registrationGroups  = rawGroups.map(DtoMapping.mapRegistrationGroup(competitionId))
      registrationPeriods = rawPeriods.map(DtoMapping.mapRegistrationPeriod(competitionId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationGroups(registrationGroups))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationPeriods(registrationPeriods))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationOpen(competitionId)(regOpen))
    } yield ()
  }.value.map(_ => ())
}
