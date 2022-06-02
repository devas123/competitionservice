package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.{assertETErr, CompetitionState}
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.{FightResultOptionConstants, FightsService}
import compman.compsrv.model.command.Commands.{GenerateBracketsCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.{BracketsAlreadyGeneratedForCategory, NoCategoryIdError, NoCompetitionIdError, NoPayloadError}
import compman.compsrv.model.Errors
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{BracketsGeneratedPayload, FightsAddedToStagePayload}
import compservice.model.protobuf.model.{StageDescriptor, StageStatus, StageType}

object GenerateBracketsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ GenerateBracketsCommand(_, _, _) => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: GenerateBracketsCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload    <- EitherT.fromOption[F](command.payload, NoPayloadError())
      categoryId <- EitherT.fromOption[F](command.categoryId, NoCategoryIdError())
      _          <- EitherT.fromOption[F](command.competitionId, NoCompetitionIdError())
      _ <- assertETErr[F](
        state.categories.exists(_.contains(categoryId)),
        Errors.CategoryDoesNotExist(command.categoryId.map(Seq(_)).getOrElse(Seq.empty))
      )
      _ <- assertETErr[F](
        !state.fights.exists(fightsMap => fightsMap.values.exists(_.categoryId == categoryId)),
        BracketsAlreadyGeneratedForCategory(categoryId)
      )
      stages = payload.stageDescriptors.toList.sortBy(_.stageOrder)
      events <- for {
        stageIdtoNewId <- EitherT.liftF(stages.traverse { s => IdOperations[F].uid.map(s.id -> _) })
        stageIdMap = stageIdtoNewId.toMap
        updatedStages <- validateAndEnrichStages[F](stages, stageIdMap, categoryId, state)
        fightAddedEvents <- EitherT.liftF(
          updatedStages.flatMap { case (stage, fights) =>
            fights.grouped(30).map { it =>
              CommandEventOperations[F, Event].create(
                EventType.FIGHTS_ADDED_TO_STAGE,
                command.competitionId,
                None,
                None,
                command.categoryId,
                Some(MessageInfo.Payload.FightsAddedToStagePayload(FightsAddedToStagePayload(it, stage.id)))
              )
            }
          }.traverse(identity)
        )
        bracketsGeneratedPayload = BracketsGeneratedPayload()
          .withStages(updatedStages.mapWithIndex((a, b) => a._1.withStageOrder(b)))
        bracketsGeneratedEvent <- EitherT
          .liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.BRACKETS_GENERATED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.BracketsGeneratedPayload(bracketsGeneratedPayload))
          ))
      } yield fightAddedEvents :+ bracketsGeneratedEvent
    } yield events
    eventT.value
  }

  private def validateAndEnrichStages[F[+_]: Monad: IdOperations: EventOperations](
    stages: List[StageDescriptor],
    stageIdMap: Map[String, String],
    categoryId: String,
    state: CompetitionState
  ) = stages.traverse(stage => validateAndEnrichStage[F](stageIdMap, categoryId, state, stage))

  private def validateAndEnrichStage[F[+_]: Monad: IdOperations: EventOperations](
    stageIdMap: Map[String, String],
    categoryId: String,
    state: CompetitionState,
    stage: StageDescriptor
  ) = for {
    duration <- EitherT.fromOption[F](Option(stage.fightDuration), Errors.InternalError("Fight duration missing"))
    outputSize <- stage.stageType match {
      case StageType.PRELIMINARY => EitherT.fromOption[F](
        stage.stageResultDescriptor.flatMap(_.outputSize),
          Errors.InternalError(s"missing output size for stage $stage")
        )
      case _ => EitherT.fromEither[F](Right(0))
    }
    stageId <- EitherT.fromOption[F](
      stageIdMap.get(stage.id),
      Errors.InternalError(s"Generated stage id not found in the map for ${stage.id}")
    )
    groupDescr <- EitherT.liftF(stage.groupDescriptors.toList.traverse { it =>
      IdOperations[F].uid.map(id => it.withId(id))
    })
    inputDescriptor = stage.getInputDescriptor
      .withSelectors(stage.getInputDescriptor.selectors.zipWithIndex.map { case (sel, index) =>
        sel.withApplyToStageId(stageIdMap(sel.applyToStageId))
      })
    enrichedOptions = stage.stageResultDescriptor.map(_.fightResultOptions).map(_.toList).getOrElse(List.empty) :+ FightResultOptionConstants.WALKOVER
    enrichedOptionsWithIds <- EitherT.liftF(
      enrichedOptions.map { it =>
        it.withLoserAdditionalPoints(it.loserAdditionalPoints.getOrElse(0))
          .withLoserPoints(it.loserPoints)
          .withWinnerAdditionalPoints(it.winnerAdditionalPoints.getOrElse(0))
          .withWinnerPoints(it.winnerPoints)
      }.traverse { it => IdOperations[F].uid.map(id => it.withId(Option(it.id).getOrElse(id))) }
    )
    resultDescriptor <- EitherT.fromOption[F](stage.stageResultDescriptor.map(_.withFightResultOptions(enrichedOptionsWithIds.distinctBy {
      _.id
    })), Errors.StageResultDescriptorMissing())
    status = if (stage.stageOrder == 0) StageStatus.WAITING_FOR_APPROVAL else StageStatus.WAITING_FOR_COMPETITORS
    stageWithIds = stage.withCategoryId(categoryId)
      .withId(stageId)
      .withStageStatus(status)
      .withCompetitionId(state.id)
      .withGroupDescriptors(groupDescr)
      .withInputDescriptor(inputDescriptor)
      .withStageResultDescriptor(resultDescriptor)

    comps =
      if (stage.stageOrder == 0) {
        state.competitors.map(_.values.toList.filter(_.categories.contains(categoryId))).getOrElse(List.empty)
      } else { List.empty }
    size = if (stage.stageOrder == 0) comps.size else stage.getInputDescriptor.numberOfCompetitors
    twoFighterFights <- EitherT(
      FightsService.bracketsGenerator[F](state.id, categoryId, stageWithIds, size, duration, comps, outputSize)
        .apply(stageWithIds.bracketType)
    )
  } yield stageWithIds
    .withName(Option(stageWithIds.getName).getOrElse("Default brackets"))
    .withStageStatus(status)
    .withInputDescriptor(inputDescriptor)
    .withStageStatus(StageStatus.WAITING_FOR_COMPETITORS)
    .withNumberOfFights(twoFighterFights.size) -> twoFighterFights
}
