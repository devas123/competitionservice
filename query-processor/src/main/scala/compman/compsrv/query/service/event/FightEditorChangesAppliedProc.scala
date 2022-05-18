package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, FightEditorChangesAppliedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.model.CompetitorDisplayInfo
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightUpdateOperations}
import compman.compsrv.Utils

object FightEditorChangesAppliedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: FightEditorChangesAppliedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: FightUpdateOperations: CompetitionQueryOperations](
    event: FightEditorChangesAppliedEvent
  ): F[Unit] = {

    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      newFights     <- OptionT.fromOption[F](Option(payload.newFights))
      updates       <- OptionT.fromOption[F](Option(payload.updates))
      removedFights <- OptionT.fromOption[F](Option(payload.removedFighids))
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      competitors <- OptionT
        .liftF(CompetitionQueryOperations.getCompetitorsByCategoryId(competitionId)(categoryId, None))
      compMap = Utils.groupById(competitors._1.map(c =>
        CompetitorDisplayInfo(c.id, Option(c.firstName), Option(c.lastName), c.academy.map(_.academyName))
      ))(_.competitorId)
      _ <- OptionT.liftF(FightUpdateOperations[F].addFights(newFights.map(DtoMapping.mapFight(compMap)).toList))
      _ <- OptionT.liftF(FightUpdateOperations[F].updateFightScores(updates.map(DtoMapping.mapFight(compMap)).toList))
      _ <- OptionT.liftF(FightUpdateOperations[F].removeFights(competitionId)(removedFights.toList))
    } yield ()
  }.value.map(_ => ())
}
