package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{Event, StageStatusUpdatedEvent}
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object StageStatusUpdatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations](): PartialFunction[Event[Any], F[Unit]] = {
    case x: StageStatusUpdatedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: StageStatusUpdatedEvent): F[Unit] = {
    for {
      payload       <- OptionT.fromOption[F](event.payload)
      competitionId <- OptionT.fromOption[F](event.competitionId)
      categoryId    <- OptionT.fromOption[F](event.categoryId)
      status        <- OptionT.fromOption[F](Option(payload.status))
      id            <- OptionT.fromOption[F](Option(payload.stageId))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateStageStatus(competitionId)(categoryId, id, status))
    } yield ()
  }.value.map(_ => ())
}
