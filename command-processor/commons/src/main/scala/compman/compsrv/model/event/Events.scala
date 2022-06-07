package compman.compsrv.model.event

import compservice.model.protobuf.commandpayload._
import compservice.model.protobuf.eventpayload._

object Events {
  sealed trait Event[+P] {
    def payload: Option[P]
    val competitionId: Option[String]
    val categoryId: Option[String]
    val competitorId: Option[String]
    val fightId: Option[String]
    val sequenceNumber: Int
  }
  final case class BracketsGeneratedEvent(
    payload: Option[BracketsGeneratedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[BracketsGeneratedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryAddedEvent(
    payload: Option[CategoryAddedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CategoryAddedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryRegistrationStatusChanged(
    payload: Option[CategoryRegistrationStatusChangePayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CategoryRegistrationStatusChangePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }
  abstract class PayloadlessEvent() extends Event[Any] {
    override def payload: Option[Any]     = None
    override val competitorId: Option[String] = None
    override val categoryId: Option[String]   = None
    override val fightId: Option[String]      = None
  }
  final case class CategoryDeletedEvent(
    competitionId: Option[String],
    override val categoryId: Option[String],
    sequenceNumber: Int
  )                                                                                             extends PayloadlessEvent
  final case class CompetitionDeletedEvent(competitionId: Option[String], sequenceNumber: Int) extends PayloadlessEvent
  final case class UnknownEvent(competitionId: Option[String], sequenceNumber: Int) extends PayloadlessEvent
  final case class CategoryBracketsDropped(
    competitionId: Option[String],
    override val categoryId: Option[String],
    sequenceNumber: Int
  ) extends PayloadlessEvent

  final case class CompetitionCreatedEvent(
    payload: Option[CompetitionCreatedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitionCreatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitionPropertiesUpdatedEvent(
    payload: Option[CompetitionPropertiesUpdatedPayload],
    competitionId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitionPropertiesUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val categoryId: Option[String]   = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitionStatusUpdatedEvent(
    payload: Option[CompetitionStatusUpdatedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitionStatusUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorAddedEvent(
    payload: Option[CompetitorAddedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitorAddedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorRemovedEvent(
    payload: Option[CompetitorRemovedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitorRemovedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorsPropagatedToStageEvent(
    payload: Option[CompetitorsPropagatedToStagePayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitorsPropagatedToStagePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorUpdatedEvent(
    payload: Option[CompetitorUpdatedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitorUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorCategoryChangedEvent(
    payload: Option[ChangeCompetitorCategoryPayload],
    competitionId: Option[String],
    sequenceNumber: Int
  ) extends Event[ChangeCompetitorCategoryPayload] {
    override val competitorId: Option[String] = None
    override val categoryId: Option[String]   = None
    override val fightId: Option[String]      = None
  }

  final case class CompetitorCategoryAddedEvent(
    payload: Option[CompetitorCategoryAddedPayload],
    competitionId: Option[String],
    sequenceNumber: Int
  ) extends Event[CompetitorCategoryAddedPayload] {
    override val competitorId: Option[String] = None
    override val categoryId: Option[String]   = None
    override val fightId: Option[String]      = None
  }

  final case class FightCompetitorsAssignedEvent(
    payload: Option[FightCompetitorsAssignedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[FightCompetitorsAssignedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class FightEditorChangesAppliedEvent(
    payload: Option[FightEditorChangesAppliedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[FightEditorChangesAppliedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class FightStartTimeCleaned(
    competitionId: Option[String],
    override val categoryId: Option[String],
    override val fightId: Option[String],
    sequenceNumber: Int
  ) extends PayloadlessEvent

  final case class ScheduleDropped(competitionId: Option[String], sequenceNumber: Int) extends PayloadlessEvent

  final case class FightOrderChangedEvent(
    payload: Option[ChangeFightOrderPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[ChangeFightOrderPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class FightsAddedToStageEvent(
    payload: Option[FightsAddedToStagePayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[FightsAddedToStagePayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class FightStartTimeUpdatedEvent(
    payload: Option[FightStartTimeUpdatedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[FightStartTimeUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class MatsUpdatedEvent(
    payload: Option[MatsUpdatedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[MatsUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }


  final case class FightResultSet(
    payload: Option[SetFightResultPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    fightId: Option[String],
    sequenceNumber: Int
  ) extends Event[SetFightResultPayload] {
    override val competitorId: Option[String] = None
  }


  final case class RegistrationInfoUpdatedEvent(
    payload: Option[RegistrationInfoUpdatedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[RegistrationInfoUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }


  final case class ScheduleGeneratedEvent(
    payload: Option[ScheduleGeneratedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[ScheduleGeneratedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class StageResultSetEvent(
    payload: Option[StageResultSetPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[StageResultSetPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class StageStatusUpdatedEvent(
    payload: Option[StageStatusUpdatedPayload],
    competitionId: Option[String],
    categoryId: Option[String],
    sequenceNumber: Int
  ) extends Event[StageStatusUpdatedPayload] {
    override val competitorId: Option[String] = None
    override val fightId: Option[String]      = None
  }

  final case class AcademyAddedEvent(payload: Option[AddAcademyPayload], sequenceNumber: Int)
      extends Event[AddAcademyPayload] {
    override val fightId: Option[String]       = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String]    = None
    override val competitorId: Option[String]  = None
  }
  final case class AcademyUpdatedEvent(payload: Option[UpdateAcademyPayload], sequenceNumber: Int)
      extends Event[UpdateAcademyPayload] {
    override val fightId: Option[String]       = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String]    = None
    override val competitorId: Option[String]  = None
  }
  final case class AcademyRemovedEvent(payload: Option[RemoveAcademyPayload], sequenceNumber: Int)
      extends Event[RemoveAcademyPayload] {
    override val fightId: Option[String]       = None
    override val competitionId: Option[String] = None
    override val categoryId: Option[String]    = None
    override val competitorId: Option[String]  = None
  }
}
