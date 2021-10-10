package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{CompetitionStatusUpdatedEvent, Event}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object CompetitionStatusUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
  : PartialFunction[Event[P], F[Unit]] = { case x: CompetitionStatusUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
                                                                                            event: CompetitionStatusUpdatedEvent
                                                                                          ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      status    <- OptionT.fromOption[F](Option(payload.getStatus))
      currentProps  <- OptionT(CompetitionQueryOperations[F].getCompetitionProperties(competitionId))
      updatedProps  <- OptionT.fromOption[F](Option(currentProps.copy(status = status)))
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].updateCompetitionProperties(updatedProps))
    } yield ()
  }.value.map(_ => ())
}
