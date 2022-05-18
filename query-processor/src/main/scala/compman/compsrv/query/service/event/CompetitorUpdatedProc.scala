package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CompetitorUpdatedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object CompetitorUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: CompetitorUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: CompetitorUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto           <- OptionT.fromOption[F](payload.competitor)
      existing      <- OptionT(CompetitionQueryOperations[F].getCompetitorById(competitionId)(dto.id))
      newComp       <- OptionT.liftF(DtoMapping.mapCompetitor[F](dto))
      updated = existing.copy(
        email = newComp.email,
        firstName = newComp.firstName,
        lastName = newComp.lastName,
        birthDate = newComp.birthDate,
        academy = newComp.academy,
        categories = newComp.categories,
        userId = newComp.userId,
        promo = newComp.promo,
        registrationStatus = newComp.registrationStatus
      )
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateCompetitor(updated))
    } yield ()
  }.value.map(_ => ())
}
