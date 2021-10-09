package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{CategoryAddedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object CategoryAddedProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: CompetitionUpdateOperations, P <: Payload](): PartialFunction[Event[P], F[Unit]] = {
    case x: CategoryAddedEvent => apply[F](x)
  }

  private def apply[F[+_]: CompetitionLogging.Service: Monad: CompetitionUpdateOperations](event: CategoryAddedEvent): F[Unit] = {
    for {
      _ <- OptionT.liftF(CompetitionLogging.Service[F].info(s"Adding category to competition ${event.competitionId}"))
      payload       <- OptionT.fromOption[F](event.payload)
      _ <- OptionT.liftF(CompetitionLogging.Service[F].info(s"Payload is: $payload"))
      competitionId <- OptionT.fromOption[F](event.competitionId)
      dto           <- OptionT.fromOption[F](Option(payload.getCategoryState))
      category      <- OptionT.liftF(DtoMapping.mapCategoryDescriptor[F](competitionId)(dto))
      _             <- OptionT.liftF(CompetitionUpdateOperations[F].addCategory(category))
    } yield ()
  }.value.map(_ => ())
}
