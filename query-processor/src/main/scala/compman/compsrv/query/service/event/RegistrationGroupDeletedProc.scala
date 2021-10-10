package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, RegistrationGroupDeletedEvent}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object RegistrationGroupDeletedProc {

  import cats.implicits._

  def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: RegistrationGroupDeletedEvent => apply[F](x)
  }

  private def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations](event: RegistrationGroupDeletedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      groupId <- OptionT.fromOption[F](Option(payload.getGroupId))
      periodId <- OptionT.fromOption[F](Option(payload.getPeriodId))
      period <- OptionT(CompetitionQueryOperations[F].getRegistrationPeriodById(competitionId)(periodId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeRegistrationGroup(competitionId)(groupId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationPeriod(period.copy(registrationGroupIds = period.registrationGroupIds - groupId)))
    } yield ()
  }.value.map(_ => ())
}
