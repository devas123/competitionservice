package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.Utils
import compman.compsrv.model.event.Events.{CompetitorsPropagatedToStageEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.model.mapping.DtoMapping.createEmptyScore
import compman.compsrv.query.model.CompetitorDisplayInfo
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations, FightQueryOperations, FightUpdateOperations}
import compservice.model.protobuf.model.{CompScore, FightReferenceType}

object CompetitorsPropagatedToStageProc {
  import cats.implicits._
  def apply[F[
    +_
  ]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations: FightQueryOperations: FightUpdateOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: CompetitorsPropagatedToStageEvent => apply[F](x) }

  private def apply[F[
    +_
  ]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations: FightUpdateOperations: FightQueryOperations](
    event: CompetitorsPropagatedToStageEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      stageId       <- OptionT.fromOption[F](Option(payload.stageId))
      propagations  <- OptionT.fromOption[F](Option(payload.propagations))
      fights        <- OptionT.liftF(FightQueryOperations[F].getFightsByStage(competitionId)(categoryId, stageId))
      competitors <- OptionT
        .liftF(CompetitionQueryOperations.getCompetitorsByCategoryId(competitionId)(categoryId, None))
      compMap = Utils.groupById(competitors._1.map(c =>
        CompetitorDisplayInfo(c.id, Option(c.firstName), Option(c.lastName), c.academy.map(_.academyName))
      ))(_.competitorId)
      fightsMap = Utils.groupById(fights)(_.id)
      updatedFights = propagations.groupBy(_.toFightId).toList.mapFilter { case (fightId, assignments) =>
        val scores = assignments.toList.mapWithIndex((ass, index) =>
          CompScore().withCompetitorId(ass.competitorId).withScore(createEmptyScore).withOrder(index)
            .withParentFightId(ass.fromFightId).withParentReferenceType(FightReferenceType.PROPAGATED)
        ).map(c => DtoMapping.mapCompScore(c, compMap.get(c.getCompetitorId)))
        for { fight <- fightsMap.get(fightId) } yield fight.copy(scores = scores)
      }
      _ <- OptionT.liftF(updatedFights.traverse(f => FightUpdateOperations[F].updateFight(f)))
    } yield ()
  }.value.map(_ => ())
}
