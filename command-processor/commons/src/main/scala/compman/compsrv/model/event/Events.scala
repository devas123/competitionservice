package compman.compsrv.model.event

import compman.compsrv.model.Payload
import compman.compsrv.model.commands.payload.{CategoryRegistrationStatusChangePayload, ChangeCompetitorCategoryPayload, ChangeFightOrderPayload, CompetitorCategoryAddedPayload, SetFightResultPayload}
import compman.compsrv.model.events.payload._

object Events {
  sealed trait Event[+P <: Payload] {
    def payload: Option[P]
    val competitionId: Option[String]
    val categoryId: Option[String]
    val competitorId: Option[String]
    val fightId: Option[String]
    val sequenceNumber: Long
  }
  final case class BracketsGeneratedEvent(
      payload: Option[BracketsGeneratedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[BracketsGeneratedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryAddedEvent(
      payload: Option[CategoryAddedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CategoryAddedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryRegistrationStatusChanged(
      payload: Option[CategoryRegistrationStatusChangePayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CategoryRegistrationStatusChangePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  abstract class PayloadlessEvent() extends Event[Payload] {
    override def payload: Option[Payload]     = None
    override val competitorId: Option[String] = None
    override val categoryId: Option[String]   = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryDeletedEvent(
      competitionId: Option[String],
      override val categoryId: Option[String],
      sequenceNumber: Long
  ) extends PayloadlessEvent
  final case class CompetitionDeletedEvent(competitionId: Option[String], sequenceNumber: Long)
      extends PayloadlessEvent
  final case class CategoryBracketsDropped(
      competitionId: Option[String],
      override val categoryId: Option[String],
      sequenceNumber: Long
  ) extends PayloadlessEvent

  final case class CompetitionCreatedEvent(
      payload: Option[CompetitionCreatedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CompetitionCreatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitionPropertiesUpdatedEvent(
      payload: Option[CompetitionPropertiesUpdatedPayload],
      competitionId: Option[String],
      sequenceNumber: Long
  ) extends Event[CompetitionPropertiesUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val categoryId: Option[String]   = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitionStatusUpdatedEvent(
      payload: Option[CompetitionStatusUpdatedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CompetitionStatusUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorAddedEvent(
      payload: Option[CompetitorAddedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CompetitorAddedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorRemovedEvent(
      payload: Option[CompetitorRemovedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CompetitorRemovedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorsPropagatedToStageEvent(
      payload: Option[CompetitorsPropagatedToStagePayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CompetitorsPropagatedToStagePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorUpdatedEvent(
      payload: Option[CompetitorUpdatedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[CompetitorUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorCategoryChangedEvent(
                                                   payload: Option[ChangeCompetitorCategoryPayload],
                                                   competitionId: Option[String],
                                                   sequenceNumber: Long
                                                 ) extends Event[ChangeCompetitorCategoryPayload] {
    override val competitorId: Option[String] = None
    override val categoryId: Option[String] = None
    override val fightId: Option[String] = None
  }

  final case class CompetitorCategoryAddedEvent(
                                                 payload: Option[CompetitorCategoryAddedPayload],
                                                 competitionId: Option[String],
                                                 sequenceNumber: Long
                                               ) extends Event[CompetitorCategoryAddedPayload] {
    override val competitorId: Option[String] = None
    override val categoryId: Option[String] = None
    override val fightId: Option[String] = None
  }

  final case class FightCompetitorsAssignedEvent(
                                                  payload: Option[FightCompetitorsAssignedPayload],
                                                  competitionId: Option[String],
                                                  categoryId: Option[String],
                                                  sequenceNumber: Long
                                                ) extends Event[FightCompetitorsAssignedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String] = None
  }

  final case class FightEditorChangesAppliedEvent(
      payload: Option[FightEditorChangesAppliedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[FightEditorChangesAppliedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class FightStartTimeCleaned(
      competitionId: Option[String],
      override val categoryId: Option[String],
      override val fightId: Option[String],
      sequenceNumber: Long
  ) extends PayloadlessEvent

  final case class ScheduleDropped(competitionId: Option[String], sequenceNumber: Long)
      extends PayloadlessEvent

  final case class FightOrderChangedEvent(
      payload: Option[ChangeFightOrderPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[ChangeFightOrderPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class FightsAddedToStageEvent(
      payload: Option[FightsAddedToStagePayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[FightsAddedToStagePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class FightStartTimeUpdatedEvent(
      payload: Option[FightStartTimeUpdatedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[FightStartTimeUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class MatsUpdatedEvent(
      payload: Option[MatsUpdatedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[MatsUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class RegistrationGroupAddedEvent(
      payload: Option[RegistrationGroupAddedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[RegistrationGroupAddedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class RegistrationGroupCategoriesAssignedEvent(
      payload: Option[RegistrationGroupCategoriesAssignedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[RegistrationGroupCategoriesAssignedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class FightResultSet(
      payload: Option[SetFightResultPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      fightId: Option[String],
      sequenceNumber: Long
  ) extends Event[SetFightResultPayload] {
    override val competitorId: Option[String] = None
  }

  final case class RegistrationGroupDeletedEvent(
      payload: Option[RegistrationGroupDeletedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[RegistrationGroupDeletedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class RegistrationInfoUpdatedEvent(
      payload: Option[RegistrationInfoUpdatedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[RegistrationInfoUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class RegistrationPeriodAddedEvent(
      payload: Option[RegistrationPeriodAddedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[RegistrationPeriodAddedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class RegistrationPeriodDeletedEvent(
      payload: Option[RegistrationPeriodDeletedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[RegistrationPeriodDeletedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class ScheduleGeneratedEvent(
      payload: Option[ScheduleGeneratedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[ScheduleGeneratedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class StageResultSetEvent(
      payload: Option[StageResultSetPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[StageResultSetPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class StageStatusUpdatedEvent(
      payload: Option[StageStatusUpdatedPayload],
      competitionId: Option[String],
      categoryId: Option[String],
      sequenceNumber: Long
  ) extends Event[StageStatusUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

}
