package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CompetitionDeletedEvent, Event}
import compman.compsrv.query.service.repository.{BlobOperations, CompetitionUpdateOperations, FightUpdateOperations}
import compman.compsrv.query.service.repository.BlobOperations.BlobService

object CompetitionDeletedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: FightUpdateOperations: BlobService](): PartialFunction[Event[Any], F[Unit]] = {
    case x: CompetitionDeletedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: FightUpdateOperations: BlobService](event: CompetitionDeletedEvent): F[Unit] = {
    for {
      competitionId    <- OptionT.fromOption[F](event.competitionId)
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeCompetitionState(competitionId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeCompetitorsForCompetition(competitionId))
      _ <- OptionT.liftF(FightUpdateOperations[F].removeFightsByCompetitionId(competitionId))
      _ <- OptionT.liftF(BlobOperations.deleteCompetitionImage[F](competitionId))
      _ <- OptionT.liftF(BlobOperations.deleteCompetitionInfo[F](competitionId))
    } yield ()
  }.value.map(_ => ())
}
