package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.fight.FightUtils
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, PropagateCompetitorsCommand}
import compman.compsrv.model.Errors.{NoPayloadError, StageDoesNotExist}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitorsPropagatedToStagePayload
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, StageDescriptor}

object PropagateCompetitorsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: PropagateCompetitorsCommand => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    command: PropagateCompetitorsCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      propagateToStage <- EitherT
        .fromOption[F](state.stages.get(payload.propagateToStageId), StageDoesNotExist(payload.propagateToStageId))
      propagatedCompetitors <- EitherT.liftF(findPropagatedCompetitors[F](propagateToStage, state))
      propagatedCompetitorsSet = propagatedCompetitors.toSet
      propagatedStageFights    = state.fights.values.filter(_.stageId == payload.propagateToStageId)
      fightsWithPropagatedCompetitors <- EitherT(SetFightResultProc.createFightsWithPropagatedCompetitors[F](
        state.competitors.values,
        propagateToStage.bracketType,
        propagatedCompetitorsSet,
        propagatedStageFights
      ))
      compAssignmentDescriptors = SetFightResultProc.createPropagationsList(fightsWithPropagatedCompetitors)
      fightUpdatedEvent <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITORS_PROPAGATED_TO_STAGE,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = None,
        payload = Option(MessageInfo.Payload.CompetitorsPropagatedToStagePayload(
          CompetitorsPropagatedToStagePayload().withStageId(propagateToStage.id)
            .withPropagations(compAssignmentDescriptors)
        ))
      ))
    } yield Seq(fightUpdatedEvent)
    eventT.value
  }

  def findPropagatedCompetitors[F[+_]: Monad: Interpreter](
    stage: StageDescriptor,
    state: CommandProcessorCompetitionState
  ): F[List[String]] = {
    val competitorResults = SetFightResultProc
      .getCompetitorResults(state.stageGraph.map(_.incomingConnections(stage.id).ids).getOrElse(Seq.empty), state)
    FightUtils
      .applyStageInputDescriptorToResultsAndFights[F](stage, state.stageGraph.get, competitorResults, state.fights)
  }
}
