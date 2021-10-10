package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, GenerateCategoriesFromRestrictionsCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.events.payload.CategoryAddedPayload

object GenerateCategoriesFromRestrictionsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x: GenerateCategoriesFromRestrictionsCommand =>
      process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: GenerateCategoriesFromRestrictionsCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload    <- EitherT.fromOption[F](command.payload, NoPayloadError())
        categories = CategoryGenerateService.generateCategories(payload)
        exists = categories.find(cat => state.categories.exists(_.contains(cat.getId)))
        events <-
          if (exists.isDefined) {
            EitherT.fromEither[F](
              Left[Errors.Error, List[EventDTO]](
                Errors.CategoryAlreadyExists(exists.get.getId, exists.get)
              )
            )
          } else {
            EitherT.liftF[F, Errors.Error, List[EventDTO]](
              categories.traverse(cat =>
                CommandEventOperations[F, EventDTO, EventType].create(
                  `type` = EventType.CATEGORY_ADDED,
                  competitorId = None,
                  competitionId = command.competitionId,
                  categoryId = Option(cat.getId),
                  payload = Option(new CategoryAddedPayload().setCategoryState(cat))
                )
              )
            )
          }
      } yield events
    eventT.value
  }
}
