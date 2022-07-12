package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CategoryDeletedEvent, Event}
import compman.compsrv.query.service.repository.{CompetitionUpdateOperations, FightUpdateOperations}

object CategoryDeletedProc {
  import cats.implicits._
  def apply[F[
    +_
  ]: Monad: CompetitionUpdateOperations: FightUpdateOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: CategoryDeletedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: FightUpdateOperations](
    event: CategoryDeletedEvent
  ): F[Unit] = {
    for {
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId <- OptionT.fromOption[F](event.categoryId)
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].removeCategory(competitionId)(categoryId))
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].removeCompetitorsForCategory(competitionId)(categoryId))
      _             <- OptionT.liftF(FightUpdateOperations[F].removeFightsForCategory(competitionId)(categoryId))
    } yield ()
  }.value.map(_ => ())
}
