package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.event.Events.{Event, FightEditorChangesAppliedEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.model.CompetitorDisplayInfo
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object FightEditorChangesAppliedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: FightEditorChangesAppliedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: FightEditorChangesAppliedEvent
  ): F[Unit] = {

    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      newFights     <- OptionT.fromOption[F](Option(payload.getNewFights))
      updates       <- OptionT.fromOption[F](Option(payload.getUpdates))
      removedFights <- OptionT.fromOption[F](Option(payload.getRemovedFighids))
      categoryId <- OptionT.fromOption[F](event.categoryId)
      competitors <- OptionT.liftF(CompetitionQueryOperations.getCompetitorsByCategoryId(competitionId)(categoryId, None))
      compMap = competitors._1.groupMapReduce(_.id)(c => CompetitorDisplayInfo(c.id, Option(c.firstName), Option(c.lastName), c.academy.map(_.academyName)))((a, _) => a)
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addFights(newFights.map(DtoMapping.mapFight(compMap)).toList))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateFightScores(updates.map(DtoMapping.mapFight(compMap)).toList))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].removeFights(competitionId)(removedFights.toList))
    } yield ()
  }.value.map(_ => ())
}
