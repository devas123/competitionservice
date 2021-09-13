package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{CompetitionCreatedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object CompetitionCreatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: CompetitionCreatedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: CompetitionCreatedEvent): F[Unit] = {
    for {
      payload          <- OptionT.fromOption[F](event.payload)
      competitionId    <- OptionT.fromOption[F](event.competitionId)
      registrationInfo <- OptionT.fromOption[F](Option(payload.getReginfo))
      rawPeriods <- OptionT.fromOption[F](Option(registrationInfo.getRegistrationPeriods).orElse(Some(Array.empty)))
      rawGroups  <- OptionT.fromOption[F](Option(registrationInfo.getRegistrationGroups).orElse(Some(Array.empty)))
      regGroups  <- OptionT.liftF(rawGroups.toList.traverse(DtoMapping.mapRegistrationGroup[F](competitionId)))
      regPeriods <- OptionT.liftF(rawPeriods.toList.traverse(DtoMapping.mapRegistrationPeriod[F](competitionId)))

      compPropertiesDTO     <- OptionT.fromOption[F](Option(payload.getProperties))
      competitionProperties <- OptionT.liftF(DtoMapping.mapCompetitionProperties[F](compPropertiesDTO))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addCompetitionProperties(competitionProperties))
      _ <- OptionT.liftF(regGroups.traverse(CompetitionUpdateOperations[F].addRegistrationGroup))
      _ <- OptionT.liftF(regPeriods.traverse(CompetitionUpdateOperations[F].addRegistrationPeriod))
    } yield ()
  }.value.map(_ => ())
}
