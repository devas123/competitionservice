package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.model.event.Events.{AcademyAddedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.AcademyOperations
import compman.compsrv.query.service.repository.AcademyOperations.AcademyService

object AcademyAddedProc {
  def apply[F[+_]: Monad: AcademyService](): PartialFunction[Event[Any], F[Unit]] = { case x: AcademyAddedEvent =>
    apply[F](x)
  }

  private def apply[F[+_]: Monad: AcademyService](event: AcademyAddedEvent): F[Unit] = {
    for {
      payload  <- OptionT.fromOption[F](event.payload)
      dto      <- OptionT.fromOption[F](Option(payload.getAcademy))
      category <- OptionT.fromOption[F](Option(DtoMapping.mapAcademy(dto)))
      _        <- OptionT.liftF(AcademyOperations.addAcademy(category))
    } yield ()
  }.value.map(_ => ())
}
