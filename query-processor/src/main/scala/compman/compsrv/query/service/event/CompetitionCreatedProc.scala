package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.event.Events.{CompetitionCreatedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object CompetitionCreatedProc {
  import cats.implicits._
  def apply[F[+_]: Monad: CompetitionUpdateOperations]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: CompetitionCreatedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: CompetitionUpdateOperations](
    event: CompetitionCreatedEvent
  ): F[Unit] = {
    for {
      payload          <- OptionT.fromOption[F](event.payload)
      competitionId    <- OptionT.fromOption[F](event.competitionId)
      registrationInfoRaw <- OptionT.fromOption[F](Option(payload.getReginfo))
      regInfo = DtoMapping.mapRegistrationInfo(competitionId)(registrationInfoRaw)
      compPropertiesDTO     <- OptionT.fromOption[F](Option(payload.getProperties))
      competitionProperties <- OptionT.liftF(DtoMapping.mapCompetitionProperties[F](compPropertiesDTO))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addCompetitionProperties(competitionProperties))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].updateRegistrationInfo(competitionId)(regInfo))
    } yield ()
  }.value.map(_ => ())
}
