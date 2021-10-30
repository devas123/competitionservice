package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightsAddedToStageEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.model.CompetitorDisplayInfo
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightUpdateOperations}

object FightsAddedToStageProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightsAddedToStageEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations](
    event: FightsAddedToStageEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      fights        <- OptionT.fromOption[F](Option(payload.getFights))
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      competitors <- OptionT
        .liftF(CompetitionQueryOperations.getCompetitorsByCategoryId(competitionId)(categoryId, None))
      compMap = competitors._1.groupMapReduce(_.id)(c =>
        CompetitorDisplayInfo(c.id, Option(c.firstName), Option(c.lastName), c.academy.map(_.academyName))
      )((a, _) => a)
      mapped = fights.map(DtoMapping.mapFight(compMap))
      _ <- OptionT.liftF(FightUpdateOperations[F].addFights(mapped.toList))
    } yield ()
  }.value.map(_ => ())
}
