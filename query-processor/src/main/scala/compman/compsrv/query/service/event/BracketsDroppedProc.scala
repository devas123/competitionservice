package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{CategoryBracketsDropped, Event}
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object BracketsDroppedProc {
  def apply[F[+_]: Monad: CompetitionUpdateOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: CategoryBracketsDropped => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: CategoryBracketsDropped): F[Unit] = {
    for {
      categoryId      <- OptionT.fromOption[F](event.categoryId)
      competitionId      <- OptionT.fromOption[F](event.competitionId)
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeStages(competitionId)(categoryId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeFightsForCategory(competitionId)(categoryId))
    } yield ()
  }.value.map(_ => ())
}
