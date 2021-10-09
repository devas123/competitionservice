package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.event.Events.{CompetitorsPropagatedToStageEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.model.mapping.DtoMapping.createEmptyScore
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object CompetitorsPropagatedToStageProc {
  import cats.implicits._

  import scala.jdk.CollectionConverters._
  def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: CompetitorsPropagatedToStageEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: CompetitorsPropagatedToStageEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId <- OptionT.fromOption[F](event.categoryId)
      stageId       <- OptionT.fromOption[F](Option(payload.getStageId))
      propagations  <- OptionT.fromOption[F](Option(payload.getPropagations).map(_.asScala))
      fights        <- OptionT.liftF(CompetitionQueryOperations.getFightsByStage(competitionId)(categoryId, stageId))
      fightsMap = fights.groupMapReduce(_.id)(identity)((a, _) => a)
      updatedFights = propagations.groupBy(_.getToFightId).toList.mapFilter { case (fightId, assignments) =>
        val scores = assignments.toList.mapWithIndex((ass, index) =>
          new CompScoreDTO().setCompetitorId(ass.getCompetitorId).setScore(createEmptyScore).setOrder(index)
            .setParentFightId(ass.getFromFightId).setParentReferenceType(FightReferenceType.PROPAGATED)
        ).map(DtoMapping.mapCompScore)
        for { fight <- fightsMap.get(fightId) } yield fight.copy(scores = scores)
      }
      _ <- OptionT.liftF(updatedFights.traverse(f => CompetitionUpdateOperations[F].addFight(f)))
    } yield ()
  }.value.map(_ => ())
}
