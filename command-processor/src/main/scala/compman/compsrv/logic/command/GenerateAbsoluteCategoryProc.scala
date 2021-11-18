package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors.{CategoryAlreadyExists, InternalError}
import compman.compsrv.model.command.Commands.{Command, GenerateAbsoluteCategoryCommand}
import compman.compsrv.model.commands.payload.CompetitorCategoryAddedPayload
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.{Errors, Payload}

object GenerateAbsoluteCategoryProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x: GenerateAbsoluteCategoryCommand =>
      addCategory[F](x, state)
  }

  private def addCategory[F[+_]: Monad: IdOperations: EventOperations](
                                                                        command: GenerateAbsoluteCategoryCommand,
                                                                        state: CompetitionState
                                                                      ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption[F](command.payload, InternalError())
        id <- EitherT
          .liftF[F, Errors.Error, String](IdOperations[F].categoryId(payload.getCategory))
        exists <- EitherT
          .fromOption[F](state.categories.map(_.contains(id)), Errors.InternalError())
        event <-
          if (exists) {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.CATEGORY_ADDED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = Some(id),
                payload = Some(new CategoryAddedPayload(payload.getCategory.setId(id)))
              )
            )
          } else {
            EitherT(
              CommandEventOperations[F, EventDTO, EventType]
                .error(CategoryAlreadyExists(id, payload.getCategory))
            )
          }
        competitorAddedEvents <- payload.getCompetitors.toList.traverse(c =>
          EitherT.liftF[F, Errors.Error, EventDTO](
            CommandEventOperations[F, EventDTO, EventType].create(
              `type` = EventType.COMPETITOR_CATEGORY_ADDED,
              competitorId = None,
              competitionId = command.competitionId,
              categoryId = Some(id),
              payload = Some(new CompetitorCategoryAddedPayload().setNewCategoryId(id).setFighterId(c))
            )
          )
        )
      } yield Seq(event) ++ competitorAddedEvents
    eventT.value
  }

}
