package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fights.CanFail
import compman.compsrv.logic.schedule.{ScheduleService, StageGraph}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, GenerateScheduleCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.{FightStartTimeUpdatedPayload, ScheduleGeneratedPayload}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO

object GenerateScheduleProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: GenerateScheduleCommand =>
    process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: GenerateScheduleCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import cats.implicits._
    def updateMatId(mat: MatDescriptionDTO) = {
      IdOperations[F].uid.map(id => mat.setId(Option(mat.getId).getOrElse(id)))
    }

    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload       <- EitherT.fromOption[F](command.payload, NoPayloadError())
      competitionId <- EitherT.fromOption[F](command.competitionId, Errors.NoCompetitionIdError())
      timeZone <- EitherT.fromOption[F](
        state.competitionProperties.flatMap(cp => Option(cp.getTimeZone)),
        Errors.InternalError("No time zone")
      )
      periods = payload.getPeriods
      _    <- assertET[F](periods != null && periods.nonEmpty, Some("No periods"))
      mats <- EitherT.liftF(payload.getMats.toList.traverse(mat => updateMatId(mat)))
      categories        = periods.flatMap(p => Option(p.getScheduleRequirements).getOrElse(Array.empty)).flatMap(e => Option(e.getCategoryIds).getOrElse(Array.empty)).toSet
      unknownCategories = state.categories.map(_.keySet).map(c => categories.diff(c)).getOrElse(Set.empty)
      _ <- assertET[F](!state.competitionProperties.exists(_.getSchedulePublished), Some("Schedule already published"))
      _ <- assertET[F](unknownCategories.isEmpty, Some(s"Categories $unknownCategories are unknown"))
      stageGraph <- EitherT.fromEither[F](getStageGraph(state))
      scheduleAndFights <-
        EitherT(ScheduleService.generateSchedule[F](competitionId, periods.toIndexedSeq, mats, stageGraph, timeZone))
      fightUpdatedEvents <- EitherT
        .liftF[F, Errors.Error, List[EventDTO]](scheduleAndFights._2.grouped(100).toList.traverse(fights =>
          CommandEventOperations[F, EventDTO, EventType].create(
            `type` = EventType.FIGHTS_START_TIME_UPDATED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = None,
            payload = Option(new FightStartTimeUpdatedPayload().setNewFights(fights.toArray))
          )
        ))
      scheduleGeneratedEvent <- EitherT
        .liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
          `type` = EventType.SCHEDULE_GENERATED,
          competitorId = None,
          competitionId = command.competitionId,
          categoryId = None,
          payload = Option(new ScheduleGeneratedPayload().setSchedule(scheduleAndFights._1))
        ))
    } yield scheduleGeneratedEvent +: fightUpdatedEvents
    eventT.value
  }

  private def getStageGraph(state: CompetitionState): CanFail[StageGraph] = {
    val categories = state.categories.getOrElse(Map.empty)
    val stages = categories.values.flatMap { it =>
      state.stages.map(_.values.filter(_.getCategoryId == it.getId)).getOrElse(Seq.empty)
    }
    val stageIds = stages.map(_.getId).toSet
    val fights   = state.fights.map(_.values.filter(f => stageIds.contains(f.getStageId))).getOrElse(List.empty)
    StageGraph.create(stages.toList, fights.toList)
  }
}
