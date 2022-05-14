package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.Utils.groupById
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.{FightsService, FightUtils}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, PropagateCompetitorsCommand}
import compman.compsrv.model.extensions._
import compman.compsrv.model.Errors.{InternalError, NoPayloadError}
import compservice.model.protobuf.commandpayload.PropagateCompetitorsPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.model.StageDescriptor
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{CompetitorAssignmentDescriptor, CompetitorsPropagatedToStagePayload}

object PropagateCompetitorsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations: Interpreter, P](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[P], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: PropagateCompetitorsCommand => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    command: PropagateCompetitorsCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      stage   <- EitherT.fromOption[F](state.stages.flatMap(_.get(payload.previousStageId)), NoPayloadError())
      propagatedCompetitors <- EitherT.liftF(findPropagatedCompetitors[F](payload, stage, state))
      propagatedStageFights <- EitherT.fromOption[F](state.fights, InternalError("Fights missing."))
        .map(_.values.filter(_.stageId == payload.propagateToStageId))
      propagatedCompetitorsSet = propagatedCompetitors.toSet
      competitorIdsToFightIds <- EitherT(
        FightsService.distributeCompetitors[F](
          state.competitors.map(_.values).map(_.filter { it => propagatedCompetitorsSet.contains(it.id) }.toList)
            .getOrElse(List.empty),
          groupById(propagatedStageFights)(_.id)
        ).apply(stage.bracketType)
      )
      compAssignmentDescriptors = competitorIdsToFightIds.flatMap(f =>
        f.competitors.map(cid => CompetitorAssignmentDescriptor().withCompetitorId(cid).withToFightId(f.id))
      )
      fightUpdatedEvent <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
        `type` = EventType.COMPETITORS_PROPAGATED_TO_STAGE,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = None,
        payload = Option(MessageInfo.Payload.CompetitorsPropagatedToStagePayload(
          CompetitorsPropagatedToStagePayload().withStageId(stage.id).withPropagations(compAssignmentDescriptors)
        ))
      ))
    } yield Seq(fightUpdatedEvent)
    eventT.value
  }

  def findPropagatedCompetitors[F[+_]: Monad: Interpreter](
    payload: PropagateCompetitorsPayload,
    stage: StageDescriptor,
    state: CompetitionState
  ): F[List[String]] = FightUtils
    .applyStageInputDescriptorToResultsAndFights[F](stage.getInputDescriptor, payload.previousStageId, state)
}
