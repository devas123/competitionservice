package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CompetitionDeletedEvent, Event}
import compman.compsrv.query.service.repository.{CompetitionUpdateOperations, FightUpdateOperations}

object CompetitionDeletedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: FightUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: CompetitionDeletedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: FightUpdateOperations](event: CompetitionDeletedEvent): F[Unit] = {
    for {
      competitionId    <- OptionT.fromOption[F](event.competitionId)
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeCompetitionState(competitionId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeCompetitorsForCompetition(competitionId))
      _ <- OptionT.liftF(FightUpdateOperations[F].removeFightsForCompetition(competitionId))
    } yield ()
  }.value.map(_ => ())
}
