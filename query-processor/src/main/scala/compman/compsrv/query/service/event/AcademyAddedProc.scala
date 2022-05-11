package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{AcademyAddedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.AcademyOperations
import compman.compsrv.query.service.repository.AcademyOperations.AcademyService

object AcademyAddedProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: AcademyService, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: AcademyAddedEvent => apply[F](x) }

  private def apply[F[+_]: CompetitionLogging.Service: Monad: AcademyService](event: AcademyAddedEvent): F[Unit] = {
    for {
      payload  <- OptionT.fromOption[F](event.payload)
      _        <- OptionT.liftF(CompetitionLogging.Service[F].info(s"Adding academy $payload"))
      dto      <- OptionT.fromOption[F](Option(payload.getAcademy))
      category <- OptionT.fromOption[F](Option(DtoMapping.mapAcademy(dto)))
      _        <- OptionT.liftF(AcademyOperations.addAcademy(category))
    } yield ()
  }.value.map(_ => ())
}
