package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, StageResultSetEvent}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, CompetitionUpdateOperations}

object StageResultSetProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionQueryOperations: CompetitionUpdateOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: StageResultSetEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations: CompetitionQueryOperations](
    event: StageResultSetEvent
  ): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId <- OptionT.fromOption[F](event.categoryId)
      stageId       <- OptionT.fromOption[F](Option(payload.stageId))
      resultsDto    <- OptionT.fromOption[F](Option(payload.results))
      stage         <- OptionT(CompetitionQueryOperations[F].getStageById(competitionId)(stageId))
      mappedResults = resultsDto.map(DtoMapping.mapCompetitorStageResult).toList
      resultDescriptor <- OptionT.fromOption[F](stage.stageResultDescriptor)
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateStage(stage.copy(stageResultDescriptor =
        Some(resultDescriptor.copy(competitorResults = mappedResults))
      )))
    } yield ()
  }.value.map(_ => ())
}
