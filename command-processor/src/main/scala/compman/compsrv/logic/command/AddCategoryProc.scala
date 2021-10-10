package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{AddCategory, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.model.Errors.{CategoryAlreadyExists, InternalError}

object AddCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ AddCategory(_, _) =>
      addCategory(x, state)
  }

  private def addCategory[F[+_]: Monad: IdOperations: EventOperations](
      command: AddCategory,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, InternalError())
        id <- EitherT
          .liftF[F, Errors.Error, String](IdOperations[F].categoryId(payload.getCategory))
        exists <- EitherT
          .fromOption(state.categories.map(_.contains(id)), Errors.InternalError())
        event <-
          if (exists) {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.CATEGORY_ADDED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = Some(id),
                payload = Some(new CategoryAddedPayload(payload.getCategory))
              )
            )
          } else {
            EitherT(
              CommandEventOperations[F, EventDTO, EventType]
                .error(CategoryAlreadyExists(id, payload.getCategory))
            )
          }
      } yield Seq(event)
    eventT.value
  }
}
