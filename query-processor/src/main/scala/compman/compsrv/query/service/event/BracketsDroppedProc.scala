package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.model.event.Events.{CategoryBracketsDropped, Event}
import compman.compsrv.query.service.repository.{
  CompetitionQueryOperations,
  CompetitionUpdateOperations,
  FightUpdateOperations
}

object BracketsDroppedProc {
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations: FightUpdateOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: CategoryBracketsDropped => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations: FightUpdateOperations](
    event: CategoryBracketsDropped
  ): F[Unit] = {
    for {
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      stages        <- OptionT.liftF(CompetitionQueryOperations[F].getStagesByCategory(competitionId)(categoryId))
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].removeStages(competitionId)(stages.map(_.id).toSet))
      _             <- OptionT.liftF(FightUpdateOperations[F].removeFightsForCategory(competitionId)(categoryId))
    } yield ()
  }.value.map(_ => ())
}
