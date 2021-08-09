package compman.compsrv.logic

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Mapping.EventMapping
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.event.Events
import compman.compsrv.model.event.Events._
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.Errors.GeneralError

object EventProcessors {
  import Operations._

  def applyEvent[F[
      +_
  ]: Monad: IdOperations: EventOperations : EventMapping, P <: Payload](
      event: Events.Event[P],
      state: CompetitionState
  ): F[Either[Errors.Error, (Seq[EventDTO], CompetitionState)]] =
    event match {
      case CategoryRegistrationStatusChanged(payload, competitionId, categoryId, sequenceNumber) => ???
      case Events.BracketsGeneratedEvent(payload, competitionId, categoryId, _)            => ???
      case Events.CategoryAddedEvent(payload, competitionId, categoryId, _)                => ???
      case Events.CompetitionCategoriesEvent(payload, competitionId, categoryId, _)        => ???
      case Events.CompetitionCreatedEvent(payload, competitionId, categoryId, _)           => ???
      case Events.CompetitionInfoEvent(payload, competitionId, categoryId, _)              => ???
      case Events.CompetitionPropertiesUpdatedEvent(payload, competitionId, _) => ???
      case Events.CompetitionStatusUpdatedEvent(payload, competitionId, categoryId, _)     => ???
      case x@Events.CompetitorAddedEvent(_, _, _, _) =>
          competitorAdded(x, state)
      case Events.CompetitorRemovedEvent(payload, competitionId, categoryId, _)                   => ???
      case Events.CompetitorsPropagatedToStageEvent(payload, competitionId, categoryId, _)        => ???
      case Events.CompetitorUpdatedEvent(payload, competitionId, categoryId, _)                   => ???
      case Events.DashboardCreatedEvent(payload, competitionId, categoryId, _)                    => ???
      case Events.FightCompetitorsAssignedEvent(payload, competitionId, categoryId, _)            => ???
      case Events.FightEditorChangesAppliedEvent(payload, competitionId, categoryId, _)           => ???
      case Events.FightPropertiesUpdatedEvent(payload, competitionId, categoryId, _)              => ???
      case Events.FightsAddedToStageEvent(payload, competitionId, categoryId, _)                  => ???
      case Events.FightStartTimeUpdatedEvent(payload, competitionId, categoryId, _)               => ???
      case Events.MatsUpdatedEvent(payload, competitionId, categoryId, _)                         => ???
      case Events.RegistrationGroupAddedEvent(payload, competitionId, categoryId, _)              => ???
      case Events.RegistrationGroupCategoriesAssignedEvent(payload, competitionId, categoryId, _) => ???
      case Events.RegistrationGroupDeletedEvent(payload, competitionId, categoryId, _)            => ???
      case Events.RegistrationInfoUpdatedEvent(payload, competitionId, categoryId, _)             => ???
      case Events.RegistrationPeriodAddedEvent(payload, competitionId, categoryId, _)             => ???
      case Events.RegistrationPeriodDeletedEvent(payload, competitionId, categoryId, _)           => ???
      case Events.ScheduleGeneratedEvent(payload, competitionId, categoryId, _)                   => ???
      case Events.StageResultSetEvent(payload, competitionId, categoryId, _)                      => ???
      case Events.StageStatusUpdatedEvent(payload, competitionId, categoryId, _)                  => ???
    }

  def competitorAdded[F[
      +_
  ]: Monad: IdOperations: EventOperations : EventMapping](
      event: CompetitorAddedEvent,
      state: CompetitionState
  ): F[Either[Errors.Error, (Seq[EventDTO], CompetitionState)]] = {
    val eventT: EitherT[F, Errors.Error, (Seq[EventDTO], CompetitionState)] =
      for {
        payload <- EitherT.fromOption(event.payload, GeneralError())
        newState = state.createCopy(
          competitors = state.competitors.map(_ :+ payload.getFighter),
          competitionProperties = state.competitionProperties,
          stages = state.stages,
          fights = state.fights,
          categories = state.categories,
          registrationInfo = state.registrationInfo,
          revision = state.revision + 1
        )
        newEvent = event.copy(sequenceNumber = state.revision)
        mapped <- EitherT.liftF(Mapping.mapEvent(newEvent))
      } yield (Seq(mapped), newState)
    eventT.value
  }
}
