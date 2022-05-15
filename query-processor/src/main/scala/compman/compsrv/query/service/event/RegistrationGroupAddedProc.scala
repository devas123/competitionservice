package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, RegistrationGroupAddedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object RegistrationGroupAddedProc {

  import cats.implicits._

  def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: RegistrationGroupAddedEvent => apply[F](x)
  }

  private def apply[F[+_] : Monad : CompetitionUpdateOperations : CompetitionQueryOperations](event: RegistrationGroupAddedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      groups <- OptionT.fromOption[F](Option(payload.groups))
      periodId <- OptionT.fromOption[F](Option(payload.periodId))
      period <- OptionT(CompetitionQueryOperations[F].getRegistrationPeriodById(competitionId)(periodId))
      mappedGroups = groups.toList.map(DtoMapping.mapRegistrationGroup(competitionId))
      existingGroups <- OptionT.fromOption[F](Option(period.registrationGroupIds).orElse(Option(Set.empty)))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addRegistrationGroups(mappedGroups))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationPeriod(period.copy(registrationGroupIds = existingGroups ++ mappedGroups.map(_.id))))
    } yield ()
  }.value.map(_ => ())
}
