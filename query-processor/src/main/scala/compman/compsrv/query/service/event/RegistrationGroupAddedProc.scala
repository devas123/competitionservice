package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, RegistrationGroupAddedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object RegistrationGroupAddedProc {

  import cats.implicits._

  def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: RegistrationGroupAddedEvent => apply[F](x)
  }

  private def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations](event: RegistrationGroupAddedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      groups <- OptionT.fromOption[F](Option(payload.getGroups))
      periodId <- OptionT.fromOption[F](Option(payload.getPeriodId))
      period <- OptionT(CompetitionQueryOperations[F].getRegistrationPeriodById(competitionId)(periodId))
      mappedGroups <- OptionT.liftF(groups.toList.traverse(DtoMapping.mapRegistrationGroup[F](competitionId)))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addRegistrationGroups(mappedGroups))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationPeriod(period.copy(registrationGroupIds = period.registrationGroupIds ++ mappedGroups.map(_.id))))
    } yield ()
  }.value.map(_ => ())
}
