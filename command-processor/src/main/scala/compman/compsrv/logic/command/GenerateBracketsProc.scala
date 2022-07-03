package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.assertETErr
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.{FightGraph, FightsService}
import compman.compsrv.logic.schedule.StageGraph
import compman.compsrv.model.command.Commands.{GenerateBracketsCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.{BracketsAlreadyGeneratedForCategory, NoCategoryIdError, NoCompetitionIdError, NoPayloadError}
import compman.compsrv.model.Errors
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{BracketsGeneratedPayload, FightsAddedToStagePayload}
import compservice.model.protobuf.model._

object GenerateBracketsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ GenerateBracketsCommand(_, _, _) => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: GenerateBracketsCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload    <- EitherT.fromOption[F](command.payload, NoPayloadError())
      categoryId <- EitherT.fromOption[F](command.categoryId, NoCategoryIdError())
      _          <- EitherT.fromOption[F](command.competitionId, NoCompetitionIdError())
      _ <- assertETErr[F](
        state.categories.contains(categoryId),
        Errors.CategoryDoesNotExist(command.categoryId.map(Seq(_)).getOrElse(Seq.empty))
      )
      _ <- assertETErr[F](
        !state.fights.values.exists(_.categoryId == categoryId),
        BracketsAlreadyGeneratedForCategory(categoryId)
      )
      stages = payload.stageDescriptors.toList.sortBy(_.stageOrder)
      _ <- assertETErr[F](
        stages.forall(s =>
          s.stageOrder == 0 ||
            s.inputDescriptor.exists(id => id.selectors.nonEmpty && id.selectors.forall(_.applyToStageId.nonEmpty))
        ),
        Errors.InputDescriptorInvalidForStage(stages)
      )
      _ <- assertETErr[F](
        stages.count(_.stageType == StageType.FINAL) == 1,
        Errors.FinalStageIsNotUniqueOrMissing(stages)
      )
      events <- for {
        stageIdtoNewId <- EitherT.liftF(stages.traverse { s => IdOperations[F].uid.map(s.id -> _) })
        stageIdMap = stageIdtoNewId.toMap
        updatedStagesAndFights <- updateStagesAndGenerateBrackets[F](stages, stageIdMap, categoryId, state)
        stagesDiGraph = StageGraph.createStagesDigraph(updatedStagesAndFights.map(_._1))
        fightAddedEvents <- EitherT.liftF(
          updatedStagesAndFights.flatMap { case (stage, fights) =>
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
        categoryFightsIndex = CategoryFightsIndex().withStageIdToFightsGraph(
          updatedStagesAndFights.map(e => e._1.id -> FightGraph.createFightsGraph(e._2)).toMap
        )
        bracketsGeneratedPayload = BracketsGeneratedPayload()
          .withStages(updatedStagesAndFights.mapWithIndex((a, b) => a._1.withStageOrder(b)))
          .withStageGraph(stagesDiGraph).withCategoryFightsIndex(categoryFightsIndex)

        bracketsGeneratedEvent <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
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

  private def updateStagesAndGenerateBrackets[F[+_]: Monad: IdOperations: EventOperations](
    stages: List[StageDescriptor],
    stageIdMap: Map[String, String],
    categoryId: String,
    state: CommandProcessorCompetitionState
  ) = stages.traverse(stage => updateStageAndGenerateFights[F](stageIdMap, categoryId, state, stage))

  private def updateStageAndGenerateFights[F[+_]: Monad: IdOperations: EventOperations](
    stageIdMap: Map[String, String],
    categoryId: String,
    state: CommandProcessorCompetitionState,
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
    _ <- assertETErr[F](
      stage.stageResultDescriptor.map(_.fightResultOptions).exists(_.nonEmpty),
      Errors.FightResultOptionsMissing(stage.id)
    )
    fightResultOptions = stage.stageResultDescriptor.map(_.fightResultOptions).map(_.toList).get

    _ <- assertETErr[F](
      stage.bracketType == BracketType.GROUP || fightResultOptions.forall(o => !o.draw),
      Errors.DrawResultsOnlyAllowedInGroups(stage.id, fightResultOptions)
    )

    groupDescr <- EitherT.liftF(stage.groupDescriptors.toList.traverse { it =>
      IdOperations[F].uid.map(id => it.withId(id))
    })
    inputDescriptor = stage.getInputDescriptor.withSelectors(stage.getInputDescriptor.selectors.map(sel =>
      sel.withApplyToStageId(stageIdMap(sel.applyToStageId))
    ))
    enrichedOptionsWithIds <- EitherT.liftF(
      fightResultOptions.map { it =>
        it.withLoserAdditionalPoints(it.loserAdditionalPoints.getOrElse(0)).withLoserPoints(it.loserPoints)
          .withWinnerAdditionalPoints(it.winnerAdditionalPoints.getOrElse(0)).withWinnerPoints(it.winnerPoints)
      }.traverse { it => IdOperations[F].uid.map(id => it.withId(Option(it.id).getOrElse(id))) }
    )
    resultDescriptor <- EitherT.fromOption[F](
      stage.stageResultDescriptor.map(_.withFightResultOptions(enrichedOptionsWithIds.distinctBy { _.id })),
      Errors.StageResultDescriptorMissing()
    )
    status = if (stage.stageOrder == 0) StageStatus.WAITING_FOR_APPROVAL else StageStatus.WAITING_FOR_COMPETITORS
    stageWithIds = stage.withCategoryId(categoryId).withId(stageId).withStageStatus(status).withCompetitionId(state.id)
      .withGroupDescriptors(groupDescr).withInputDescriptor(inputDescriptor).withStageResultDescriptor(resultDescriptor)

    comps =
      if (stage.stageOrder == 0) { state.competitors.values.toList.filter(_.categories.contains(categoryId)) }
      else { List.empty }
    size = if (stage.stageOrder == 0) comps.size else stage.getInputDescriptor.numberOfCompetitors
    twoFighterFights <- EitherT(
      FightsService.bracketsGenerator[F](state.id, categoryId, stageWithIds, size, duration, comps, outputSize)
        .apply(stageWithIds.bracketType)
    )
  } yield stageWithIds.withName(Option(stageWithIds.getName).getOrElse("Default brackets")).withStageStatus(status).withInputDescriptor(inputDescriptor).withStageStatus(StageStatus.WAITING_FOR_COMPETITORS).withNumberOfFights(twoFighterFights.size) -> twoFighterFights
}
