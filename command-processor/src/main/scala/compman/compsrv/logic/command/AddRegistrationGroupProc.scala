package compman.compsrv.logic.command

import cats.{Monad, Traverse}
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{AddRegistrationGroupCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.{
  NoPayloadError,
  RegistrationGroupAlreadyExistsError,
  RegistrationGroupDefaultAlreadyExistsError
}
import compservice.model.protobuf.common.MessageInfo.Payload
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.RegistrationGroupAddedPayload
import compservice.model.protobuf.model.RegistrationGroup

object AddRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[P], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ AddRegistrationGroupCommand(_, _, _) => addRegistrationGroup(x, state)
  }

  private def addRegistrationGroup[F[+_]: Monad: IdOperations: EventOperations](
    command: AddRegistrationGroupCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      groupsWithIds <- EitherT
        .liftF[F, Errors.Error, List[RegistrationGroup]](Traverse[List].traverse(payload.groups.toList)(g =>
          Monad[F].map(IdOperations[F].registrationGroupId(g))(id => g.withId(id))
        ))
      exists <- EitherT.right(Monad[F].pure(
        (for {
          regInfo <- state.registrationInfo
          idSet                = groupsWithIds.map(_.id).toSet
          defaultGroupsWithIds = groupsWithIds.filter(_.defaultGroup).map(_.id).toSet
          defaultGroupAlreadyExists = defaultGroupsWithIds.nonEmpty &&
            regInfo.registrationGroups.exists { case (id, r) => !defaultGroupsWithIds.contains(id) && r.defaultGroup }
          groups <- Option(regInfo.registrationGroups)
        } yield (idSet.filter(groups.contains), defaultGroupAlreadyExists)).getOrElse((Set.empty[String], false))
      ))
      event <-
        if (exists._1.isEmpty && !exists._2) {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
            `type` = EventType.REGISTRATION_GROUP_ADDED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = None,
            payload = Some(Payload.RegistrationGroupAddedPayload(
              RegistrationGroupAddedPayload(payload.periodId, groupsWithIds.toArray)
            ))
          ))
        } else if (exists._1.nonEmpty) {
          EitherT(CommandEventOperations[F, Event, EventType].error(RegistrationGroupAlreadyExistsError(exists._1)))
        } else {
          EitherT(CommandEventOperations[F, Event, EventType].error(RegistrationGroupDefaultAlreadyExistsError()))
        }

    } yield Seq(event)
    eventT.value
  }
}
