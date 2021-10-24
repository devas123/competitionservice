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
    case x: CompetitorCategoryChangedEvent => apply[F](x.competitionId, x.payload.flatMap(p => Option(p.getFighterId)), x.payload.flatMap(p => Option(p.getOldCategoryId)), x.payload.flatMap(p => Option(p.getNewCategoryId)))
    case x: CompetitorCategoryAddedEvent => apply[F](x.competitionId, x.payload.flatMap(p => Option(p.getFighterId)), None, x.payload.flatMap(p => Option(p.getNewCategoryId)))
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
                                                                                            competitionId: Option[String],
                                                                                            competitorId: Option[String],
                                                                                            oldCategoryId: Option[String],
                                                                                            newCategoryId: Option[String]
  ): F[Unit] = {
    for {
      competitionId <- OptionT.fromOption[F](competitionId)
      competitorId           <- OptionT.fromOption[F](competitorId)
      newCategory           <- OptionT.fromOption[F](newCategoryId)
      existing      <- OptionT(CompetitionQueryOperations[F].getCompetitorById(competitionId)(competitorId))
      newCategories = oldCategoryId.map(oc => existing.categories - oc).getOrElse(existing.categories) + newCategory
      updated = existing.copy(
        categories = newCategories,
      )
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateCompetitor(updated))
    } yield ()
  }.value.map(_ => ())
}
