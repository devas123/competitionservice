package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{ChangeCompetitorCategoryCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError

object ChangeCompetitorCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ ChangeCompetitorCategoryCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: ChangeCompetitorCategoryCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      newCategoryExists = state.categories
        .exists(_.keySet.intersect(payload.getNewCategories.toSet) == payload.getNewCategories.toSet)
      fighterExists = state.competitors.exists(_.contains(payload.getFighterId))
      event <-
        if (payload.getNewCategories.isEmpty) {
          EitherT.fromEither(Left[Errors.Error, EventDTO](Errors.CategoryListIsEmpty()))
        }
        else if (!fighterExists) {
          EitherT.fromEither(Left[Errors.Error, EventDTO](Errors.CompetitorDoesNotExist(payload.getFighterId)))
        } else if (!newCategoryExists) {
          EitherT.fromEither(Left[Errors.Error, EventDTO](Errors.CategoryDoesNotExist(
            state.categories.map(cs => payload.getNewCategories.toSet.diff(cs.keySet).toArray).getOrElse(Array.empty)
          )))
        } else {
          EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
            `type` = EventType.COMPETITOR_CATEGORY_CHANGED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(payload)
          ))
        }
    } yield Seq(event)
    eventT.value
  }
}
