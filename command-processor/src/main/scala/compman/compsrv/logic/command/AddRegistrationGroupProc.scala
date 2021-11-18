package compman.compsrv.logic.command

import cats.{Monad, Traverse}
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{AddRegistrationGroupCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.RegistrationGroupAddedPayload
import compman.compsrv.model.Errors.{InternalError, NoPayloadError}

object AddRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ AddRegistrationGroupCommand(_, _, _) =>
      addRegistrationGroup(x, state)
  }

  private def addRegistrationGroup[F[+_]: Monad: IdOperations: EventOperations](
      command: AddRegistrationGroupCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, NoPayloadError())
        ids <- EitherT
          .liftF[F, Errors.Error, List[String]](Traverse[List].traverse(payload.getGroups.toList)(g => IdOperations[F].registrationGroupId(g)))
        exists <- EitherT.right(
          Monad[F].pure(
            (
              for {
                regInfo <- state.registrationInfo
                idSet = ids.toSet
                groups <- Option(regInfo.getRegistrationGroups)
              } yield groups.exists(g => idSet.contains(g.getId))
            ).getOrElse(false)
          )
        )
        event <-
          if (exists) {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.REGISTRATION_PERIOD_ADDED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = None,
                payload = Some(new RegistrationGroupAddedPayload(payload.getPeriodId, payload.getGroups))
              )
            )
          } else {
            EitherT(CommandEventOperations[F, EventDTO, EventType].error(InternalError()))
          }
      } yield Seq(event)
    eventT.value
  }
}
