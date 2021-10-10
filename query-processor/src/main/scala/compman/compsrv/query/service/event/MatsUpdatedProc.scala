package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, MatsUpdatedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object MatsUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: MatsUpdatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: MatsUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto           <- OptionT.fromOption[F](Option(payload.getMats))
      newMatsByPeriods = dto.groupMap(_.getPeriodId)(DtoMapping.mapMat)
      periods <- OptionT.liftF(CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId))
      updatedPeriods = periods.map(o => o.copy(mats = newMatsByPeriods.getOrElse(o.id, Array.empty).toList))
      _ <- OptionT.liftF(dto.toList.traverse { m =>
        for {
          fights <- CompetitionQueryOperations[F].getFightsByMat(competitionId)(m.getId, Int.MaxValue)
          newMat = DtoMapping.mapMat(m)
          updatedFights = fights.map(f => f.copy(scheduleInfo = f.scheduleInfo.map(_.copy(mat = newMat)), matId = Option(m.getId)))
          _ <- CompetitionUpdateOperations[F].updateFights(updatedFights)
        } yield ()
      })
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updatePeriods(updatedPeriods))
    } yield ()
  }.value.map(_ => ())
}
