package compman.compsrv.logic.command

import cats.{Monad, Traverse}
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{AddRegistrationGroupCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.RegistrationGroupAddedPayload
import compman.compsrv.model.Errors.{
  NoPayloadError,
  RegistrationGroupAlreadyExistsError,
  RegistrationGroupDefaultAlreadyExistsError
}
import compman.compsrv.model.dto.competition.RegistrationGroupDTO

import scala.jdk.CollectionConverters._

object AddRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ AddRegistrationGroupCommand(_, _, _) => addRegistrationGroup(x, state)
  }

  private def addRegistrationGroup[F[+_]: Monad: IdOperations: EventOperations](
    command: AddRegistrationGroupCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      groupsWithIds <- EitherT
        .liftF[F, Errors.Error, List[RegistrationGroupDTO]](Traverse[List].traverse(payload.getGroups.toList)(g =>
          Monad[F].map(IdOperations[F].registrationGroupId(g))(id => g.setId(id))
        ))
      exists <- EitherT.right(Monad[F].pure(
        (for {
          regInfo <- state.registrationInfo
          idSet                = groupsWithIds.map(_.getId).toSet
          defaultGroupsWithIds = groupsWithIds.filter(_.getDefaultGroup == true).map(_.getId).toSet
          defaultGroupAlreadyExists = defaultGroupsWithIds.nonEmpty && regInfo.getRegistrationGroups != null &&
            regInfo.getRegistrationGroups.asScala.exists { case (id, r) =>
              !defaultGroupsWithIds.contains(id) && r.getDefaultGroup
            }
          groups <- Option(regInfo.getRegistrationGroups)
        } yield (idSet.filter(groups.containsKey), defaultGroupAlreadyExists)).getOrElse((Set.empty[String], false))
      ))
      event <-
        if (exists._1.isEmpty && !exists._2) {
          EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
            `type` = EventType.REGISTRATION_GROUP_ADDED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = None,
            payload = Some(new RegistrationGroupAddedPayload(payload.getPeriodId, groupsWithIds.toArray))
          ))
        } else if (exists._1.nonEmpty) {
          EitherT(CommandEventOperations[F, EventDTO, EventType].error(RegistrationGroupAlreadyExistsError(exists._1)))
        } else {
          EitherT(CommandEventOperations[F, EventDTO, EventType].error(RegistrationGroupDefaultAlreadyExistsError()))
        }

    } yield Seq(event)
    eventT.value
  }
}
