package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightsAddedToStageEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object FightsAddedToStageProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightsAddedToStageEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: FightsAddedToStageEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      fights        <- OptionT.fromOption[F](Option(payload.getFights))
      periods       <- OptionT.liftF(CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId))
      mats   = periods.flatMap(_.mats).groupMapReduce(_.matId)(identity)((a, _) => a)
      mapped = fights.map(f => DtoMapping.mapFight(f))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addFights(mapped.toIndexedSeq))
    } yield ()
  }.value.map(_ => ())
}
