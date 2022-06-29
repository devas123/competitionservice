package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.CanFail
import compman.compsrv.logic.schedule.{ScheduleService, StageGraph}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{GenerateScheduleCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{FightStartTimeUpdatedPayload, ScheduleGeneratedPayload}
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, DiGraph, MatDescription}

object GenerateScheduleProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: GenerateScheduleCommand => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: GenerateScheduleCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    import cats.implicits._
    def updateMatId(mat: MatDescription) = {
      IdOperations[F].uid.map(id => mat.withId(if (mat.id.isEmpty) id else mat.id))
    }
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload       <- EitherT.fromOption[F](command.payload, NoPayloadError())
      competitionId <- EitherT.fromOption[F](command.competitionId, Errors.NoCompetitionIdError())
      timeZone <- EitherT.fromOption[F](
        state.competitionProperties.flatMap(cp => Option(cp.timeZone)),
        Errors.InternalError("No time zone")
      )
      periods = payload.periods
      _    <- assertET[F](periods.nonEmpty, Some("No periods"))
      mats <- EitherT.liftF(payload.mats.toList.traverse(mat => updateMatId(mat)))
      categories = periods.flatMap(p => Option(p.scheduleRequirements).getOrElse(Seq.empty)).flatMap(e => e.categoryIds)
        .toSet
      unknownCategories = categories.diff(state.categories.keySet)
      _ <- assertET[F](!state.schedule.exists(s => s.periods.nonEmpty), Some("Schedule generated"))
      _ <- assertET[F](!state.competitionProperties.exists(_.schedulePublished), Some("Schedule already published"))
      _ <- assertET[F](unknownCategories.isEmpty, Some(s"Categories $unknownCategories are unknown"))
      stageDigraph <- EitherT.fromOption[F](state.stageGraph, Errors.NoStageDigraphError())
      stageGraph <- EitherT.fromEither[F](getStageGraph(state, stageDigraph))
      scheduleAndFights <-
        EitherT(ScheduleService.generateSchedule[F](competitionId, periods.toIndexedSeq, mats, stageGraph, timeZone))
      fightUpdatedEvents <- EitherT
        .liftF[F, Errors.Error, List[Event]](scheduleAndFights._2.grouped(100).toList.traverse(fights =>
          CommandEventOperations[F, Event].create(
            `type` = EventType.FIGHTS_START_TIME_UPDATED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = None,
            payload = Option(
              MessageInfo.Payload.FightStartTimeUpdatedPayload(FightStartTimeUpdatedPayload().withNewFights(fights))
            )
          )
        ))
      scheduleGeneratedEvent <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.SCHEDULE_GENERATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = None,
        payload = Option(
          MessageInfo.Payload.ScheduleGeneratedPayload(ScheduleGeneratedPayload().withSchedule(scheduleAndFights._1))
        )
      ))
    } yield scheduleGeneratedEvent +: fightUpdatedEvents
    eventT.value
  }

  private def getStageGraph(state: CommandProcessorCompetitionState, stageDigraph: DiGraph): CanFail[StageGraph] = {
    val stages     = state.stages
    val stageIds   = stages.keySet
    val fights     = state.fights.values.filter(f => stageIds.contains(f.stageId))
    StageGraph.create(stages.values.toList, stageDigraph, fights.toList)
  }
}
