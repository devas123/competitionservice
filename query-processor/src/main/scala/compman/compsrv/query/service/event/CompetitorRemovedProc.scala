package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CompetitorRemovedEvent, Event}
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object CompetitorRemovedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: CompetitorRemovedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: CompetitorRemovedEvent): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      competitorId  <- OptionT.fromOption[F](Option(payload.fighterId))
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].removeCompetitor(competitionId)(competitorId))
    } yield ()
  }.value.map(_ => ())
}
