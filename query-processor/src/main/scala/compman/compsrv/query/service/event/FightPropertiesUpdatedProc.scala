package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightPropertiesUpdatedEvent}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object FightPropertiesUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightPropertiesUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: FightPropertiesUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto           <- OptionT.fromOption[F](Option(payload.getUpdate))
      existing      <- OptionT(CompetitionQueryOperations[F].getFightById(competitionId)(dto.getFightId))
      scheduleInfo = existing.scheduleInfo
      mat          = Option(dto.getMatId)
      updatedSchedule = scheduleInfo
        .copy(matId = mat, numberOnMat = Option(dto.getNumberOnMat), startTime = Option(dto.getStartTime))
      _ <- OptionT
        .liftF(CompetitionUpdateOperations[F].updateFight(existing.copy(scheduleInfo = updatedSchedule)))
    } yield ()
  }.value.map(_ => ())
}
