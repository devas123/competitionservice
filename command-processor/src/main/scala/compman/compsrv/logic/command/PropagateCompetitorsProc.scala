package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.Utils.groupById
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.extensions._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fights.{FightUtils, FightsService}
import compman.compsrv.logic.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, PropagateCompetitorsCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.{CompetitorAssignmentDescriptor, CompetitorsPropagatedToStagePayload}
import compman.compsrv.model.Errors.{InternalError, NoPayloadError}
import compman.compsrv.model.commands.payload.PropagateCompetitorsPayload
import compman.compsrv.model.dto.brackets.StageDescriptorDTO

import scala.jdk.CollectionConverters._

object PropagateCompetitorsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations: Interpreter, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: PropagateCompetitorsCommand =>
    process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    command: PropagateCompetitorsCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      stage   <- EitherT.fromOption[F](state.stages.flatMap(_.get(payload.getPreviousStageId)), NoPayloadError())
      propagatedCompetitors <- EitherT.liftF(findPropagatedCompetitors[F](payload, stage, state))
      propagatedStageFights <- EitherT.fromOption[F](state.fights, InternalError("Fights missing."))
        .map(_.values.filter(_.getStageId == payload.getPropagateToStageId))
      propagatedCompetitorsSet = propagatedCompetitors.toSet
      competitorIdsToFightIds <- EitherT(
        FightsService.distributeCompetitors[F](
          state.competitors.map(_.values).map(_.filter { it => propagatedCompetitorsSet.contains(it.getId) }.toList)
            .getOrElse(List.empty),
          groupById(propagatedStageFights)(_.getId)
        ).apply(stage.getBracketType)
      )
      compAssignmentDescriptors = competitorIdsToFightIds.flatMap(f =>
        f.competitors.map(cid => new CompetitorAssignmentDescriptor().setCompetitorId(cid).setToFightId(f.getId))
      )
      fightUpdatedEvent <- EitherT
        .liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
          `type` = EventType.COMPETITORS_PROPAGATED_TO_STAGE,
          competitorId = None,
          competitionId = command.competitionId,
          categoryId = None,
          payload = Option(
            new CompetitorsPropagatedToStagePayload().setStageId(stage.getId)
              .setPropagations(compAssignmentDescriptors.asJava)
          )
        ))
    } yield Seq(fightUpdatedEvent)
    eventT.value
  }

  def findPropagatedCompetitors[F[+_]: Monad: Interpreter](
    payload: PropagateCompetitorsPayload,
    stage: StageDescriptorDTO,
    state: CompetitionState
  ): F[List[String]] = FightUtils
    .applyStageInputDescriptorToResultsAndFights[F](stage.getInputDescriptor, payload.getPreviousStageId, state)
}
