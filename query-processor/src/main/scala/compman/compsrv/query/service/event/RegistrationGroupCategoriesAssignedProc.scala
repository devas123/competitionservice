package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, RegistrationGroupCategoriesAssignedEvent}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object RegistrationGroupCategoriesAssignedProc {

  import cats.implicits._

  def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: RegistrationGroupCategoriesAssignedEvent => apply[F](x)
  }

  private def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations](event: RegistrationGroupCategoriesAssignedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      groupId <- OptionT.fromOption[F](Option(payload.groupId))
      categories <- OptionT.fromOption[F](Option(payload.categories))
      group <- OptionT(CompetitionQueryOperations[F].getRegistrationGroupById(competitionId)(groupId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationGroup(group.copy(categories = categories.toSet)))
    } yield ()
  }.value.map(_ => ())
}
