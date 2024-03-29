package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CompetitorAddedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object CompetitorAddedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: CompetitorAddedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: CompetitorAddedEvent): F[Unit] = {
    for {
      payload    <- OptionT.fromOption[F](event.payload)
      dto        <- OptionT.fromOption[F](payload.competitor)
      competitor = DtoMapping.mapCompetitor(dto)
      _          <- OptionT.liftF(CompetitionUpdateOperations[F].addCompetitor(competitor))
    } yield ()
  }.value.map(_ => ())
}
