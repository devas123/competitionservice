package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CompetitorCategoryAddedEvent, CompetitorCategoryChangedEvent, Event}
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object CompetitorCategoryChangesProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = {
    case x: CompetitorCategoryChangedEvent => changeCategory[F](x.competitionId, x.payload.flatMap(p => Option(p.fighterId)), x.payload.flatMap(p => Option(p.newCategories)))
    case x: CompetitorCategoryAddedEvent => changeCategory[F](x.competitionId, x.payload.flatMap(p => Option(p.fighterId)), x.payload.flatMap(p => Option(Array(p.newCategoryId))))
  }

  private def changeCategory[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
                                                                                            competitionId: Option[String],
                                                                                            competitorId: Option[String],
                                                                                            newCategoryIds: Option[Seq[String]]
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
