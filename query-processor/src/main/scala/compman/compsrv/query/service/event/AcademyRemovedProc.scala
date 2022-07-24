package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import cats.implicits.toFunctorOps
import compman.compsrv.model.event.Events.{AcademyRemovedEvent, Event}
import compman.compsrv.query.service.repository.AcademyOperations
import compman.compsrv.query.service.repository.AcademyOperations.AcademyService

object AcademyRemovedProc {
  def apply[F[+_]: Monad: AcademyService]()
    : PartialFunction[Event[Any], F[Unit]] = { case x: AcademyRemovedEvent => apply[F](x) }

  private def apply[F[+_]: Monad: AcademyService](event: AcademyRemovedEvent): F[Unit] = {
    for {
      payload <- OptionT.fromOption[F](event.payload)
      id      <- OptionT.fromOption[F](Option(payload.academyId))
      _       <- OptionT.liftF(AcademyOperations.deleteAcademy(id))
    } yield ()
  }.value.map(_ => ())
}
