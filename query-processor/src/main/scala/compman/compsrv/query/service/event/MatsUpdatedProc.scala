package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, MatsUpdatedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations, FightQueryOperations, FightUpdateOperations}

object MatsUpdatedProc {
  import cats.implicits._
  def apply[F[
    +_
  ]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations: FightUpdateOperations: FightQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: MatsUpdatedEvent => apply[F](x) }

  private def apply[F[
    +_
  ]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations: FightUpdateOperations: FightQueryOperations](
    event: MatsUpdatedEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto           <- OptionT.fromOption[F](Option(payload.mats))
      newMatsByPeriods = dto.groupMap(_.periodId)(DtoMapping.mapMat)
      periods <- OptionT.liftF(CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId))
      updatedPeriods = periods.map(o => o.copy(mats = newMatsByPeriods.getOrElse(o.id, Seq.empty).toList))
      _ <- OptionT.liftF(dto.toList.traverse { m =>
        for {
          fights <- FightQueryOperations[F].getFightsByMat(competitionId)(m.id, Int.MaxValue)
          newMat = DtoMapping.mapMat(m)
          updatedFights = fights.map(f =>
            f.copy(matName = Option(newMat.name), matOrder = Option(newMat.matOrder), matId = Option(m.id))
          )
          _ <- FightUpdateOperations[F].updateFightScores(updatedFights)
        } yield ()
      })
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updatePeriods(updatedPeriods))
    } yield ()
  }.value.map(_ => ())
}
