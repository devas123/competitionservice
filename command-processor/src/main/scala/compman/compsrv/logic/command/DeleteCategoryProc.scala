package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, DeleteCategoryCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoCategoryIdError

object DeleteCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload]()
    : PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: DeleteCategoryCommand =>
    process(x)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: DeleteCategoryCommand
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      _ <- logic.assertETErr[F](command.categoryId.isDefined, NoCategoryIdError())
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.CATEGORY_DELETED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = None
      ))
    } yield Seq(event)
    eventT.value
  }
}
