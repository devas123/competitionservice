package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, RegistrationGroupDeletedEvent}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object RegistrationGroupDeletedProc {

  import cats.implicits._

  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: RegistrationGroupDeletedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: RegistrationGroupDeletedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      groupId       <- OptionT.fromOption[F](Option(payload.groupId))
      periodId      <- OptionT.fromOption[F](Option(payload.periodId))
      periods       <- OptionT.liftF(CompetitionQueryOperations[F].getRegistrationPeriods(competitionId))
      period        <- OptionT.fromOption[F](periods.find(_.id == periodId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationPeriod(period.copy(registrationGroupIds =
        period.registrationGroupIds - groupId
      )))
      shouldRemoveGroup = periods.filter(_.id != periodId).forall(per => !per.registrationGroupIds.contains(groupId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeRegistrationGroup(competitionId)(groupId))
        .whenA(shouldRemoveGroup)
    } yield ()
  }.value.map(_ => ())
}
