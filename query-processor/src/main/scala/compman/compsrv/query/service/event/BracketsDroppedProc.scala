package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.model.event.Events.{CategoryBracketsDropped, Event}
import compman.compsrv.query.service.repository.{CompetitionUpdateOperations, FightUpdateOperations}

object BracketsDroppedProc {
  def apply[F[+_]: Monad: CompetitionUpdateOperations: FightUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: CategoryBracketsDropped => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: FightUpdateOperations](event: CategoryBracketsDropped): F[Unit] = {
    for {
      categoryId      <- OptionT.fromOption[F](event.categoryId)
      competitionId      <- OptionT.fromOption[F](event.competitionId)
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeStages(competitionId)(categoryId))
      _ <- OptionT.liftF(FightUpdateOperations[F].removeFightsForCategory(competitionId)(categoryId))
    } yield ()
  }.value.map(_ => ())
}
