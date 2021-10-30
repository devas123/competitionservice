package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightPropertiesUpdatedEvent}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightQueryOperations, FightUpdateOperations}

import java.util.Date

object FightPropertiesUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations: FightQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightPropertiesUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations: FightQueryOperations](
    event: FightPropertiesUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId    <- OptionT.fromOption[F](event.competitionId)
      dto           <- OptionT.fromOption[F](Option(payload.getUpdate))
      existing      <- OptionT(FightQueryOperations[F].getFightById(competitionId)(categoryId, dto.getFightId))
      periodId      <- OptionT.fromOption[F](existing.periodId)
      periods       <- OptionT(CompetitionQueryOperations[F].getPeriodById(competitionId)(periodId))
      mats = periods.mats.groupMapReduce(_.matId)(identity)((a, _) => a)
      matId <- OptionT.fromOption[F](Option(dto.getMatId))
      mat = mats.get(matId)
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFight(existing.copy(
        matId = mat.map(_.matId),
        matName = mat.map(_.name),
        matOrder = mat.map(_.matOrder),
        numberOnMat = Option(dto.getNumberOnMat),
        startTime = Option(dto.getStartTime).map(Date.from)
      )))
    } yield ()
  }.value.map(_ => ())
}
