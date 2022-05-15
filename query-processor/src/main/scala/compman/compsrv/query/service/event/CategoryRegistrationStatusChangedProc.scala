package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits.toFunctorOps
import compman.compsrv.model.event.Events.{CategoryRegistrationStatusChanged, Event}
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object CategoryRegistrationStatusChangedProc {
  def apply[F[+_]: Monad: CompetitionUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x:  CategoryRegistrationStatusChanged => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: CategoryRegistrationStatusChanged): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId <- OptionT.fromOption[F](event.categoryId)
      newStatus           <- OptionT.fromOption[F](Option(payload.newStatus))
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].updateCategoryRegistrationStatus(competitionId)(categoryId, newStatus))
    } yield ()
  }.value.map(_ => ())
}
