package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{CompetitorCategoryAddedEvent, CompetitorCategoryChangedEvent, Event}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object CompetitorCategoryChangesProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = {
    case x: CompetitorCategoryChangedEvent => apply[F](x.competitionId, x.payload.flatMap(p => Option(p.getFighterId)), x.payload.flatMap(p => Option(p.getOldCategories)), x.payload.flatMap(p => Option(p.getNewCategories)))
    case x: CompetitorCategoryAddedEvent => apply[F](x.competitionId, x.payload.flatMap(p => Option(p.getFighterId)), None, x.payload.flatMap(p => Option(Array(p.getNewCategoryId))))
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
                                                                                            competitionId: Option[String],
                                                                                            competitorId: Option[String],
                                                                                            oldCategoryIds: Option[Array[String]],
                                                                                            newCategoryIds: Option[Array[String]]
  ): F[Unit] = {
    for {
      competitionId <- OptionT.fromOption[F](competitionId)
      competitorId           <- OptionT.fromOption[F](competitorId)
      newCategories           <- OptionT.fromOption[F](newCategoryIds)
      existing      <- OptionT(CompetitionQueryOperations[F].getCompetitorById(competitionId)(competitorId))
      updated = existing.copy(
        categories = newCategories.toSet,
      )
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateCompetitor(updated))
    } yield ()
  }.value.map(_ => ())
}
