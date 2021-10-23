package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{CompetitionPropertiesUpdatedEvent, Event}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object CompetitionPropertiesUpdatedProc {
  import cats.implicits._
  import compman.compsrv.query.model.extensions._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: CompetitionPropertiesUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: CompetitionPropertiesUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      properties    <- OptionT.fromOption[F](Option(payload.getProperties))
      currentProps  <- OptionT(CompetitionQueryOperations[F].getCompetitionProperties(competitionId))
      updatedProps  <- OptionT.fromOption[F](Option(currentProps.applyProperties(properties)))
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].updateCompetitionProperties(updatedProps))
    } yield ()
  }.value.map(_ => ())
}
