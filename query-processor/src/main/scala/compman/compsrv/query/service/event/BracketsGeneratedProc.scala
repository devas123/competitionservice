package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{BracketsGeneratedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object BracketsGeneratedProc {
  def apply[F[+_]: Monad: CompetitionUpdateOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: BracketsGeneratedEvent => apply[F](x)
  }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](event: BracketsGeneratedEvent): F[Unit] = {
    for {
      payload      <- OptionT.fromOption[F](event.payload)
      rawStages <- OptionT.fromOption[F](Option(payload.getStages))
      mappedStages <- rawStages.toList.traverse(dto => OptionT.liftF(DtoMapping.mapStageDescriptor[F](dto)))
      _            <- OptionT.liftF(mappedStages.traverse(CompetitionUpdateOperations[F].addStage(_)))
    } yield ()
  }.value.map(_ => ())
}
