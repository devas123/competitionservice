package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fights.FightsService
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, GenerateBracketsCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.{NoCategoryIdError, NoCompetitionIdError, NoPayloadError}
import compman.compsrv.model.dto.brackets.{FightResultOptionDTO, StageDescriptorDTO, StageStatus, StageType}
import compman.compsrv.model.events.payload.{BracketsGeneratedPayload, FightsAddedToStagePayload}

object GenerateBracketsProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ GenerateBracketsCommand(_, _, _) => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: GenerateBracketsCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload    <- EitherT.fromOption[F](command.payload, NoPayloadError())
      categoryId <- EitherT.fromOption[F](command.categoryId, NoCategoryIdError())
      _          <- EitherT.fromOption[F](command.competitionId, NoCompetitionIdError())
      exists = state.categories.exists(_.contains(categoryId))
      events <-
        if (!exists) {
          EitherT.fromEither[F](Left[Errors.Error, List[EventDTO]](Errors.CategoryDoesNotExist(
            command.categoryId.map(Array(_)).getOrElse(Array.empty)
          )))
        } else {
          val stages = payload.getStageDescriptors.toList.sortBy(_.getStageOrder)
          for {
            stageIdtoNewId <- EitherT.liftF(stages.traverse { s => IdOperations[F].uid.map(s.getId -> _) })
            stageIdMap = stageIdtoNewId.toMap
            updatedStages <- validateAndEnrichStages[F](stages, stageIdMap, categoryId, state)
            fightAddedEvents <- EitherT.liftF(
              updatedStages.flatMap { case (stage, fights) =>
                fights.grouped(30).map { it =>
                  CommandEventOperations[F, EventDTO, EventType].create(
                    EventType.FIGHTS_ADDED_TO_STAGE,
                    command.competitionId,
                    None,
                    None,
                    command.categoryId,
                    Some(new FightsAddedToStagePayload(it.toArray, stage.getId))
                  )
                }
              }.traverse(identity)
            )
            bracketsGeneratedPayload = {
              val p = new BracketsGeneratedPayload()
              p.setStages(updatedStages.mapWithIndex((a, b) => a._1.setStageOrder(b)).toArray)
              p
            }
            bracketsGeneratedEvent <- EitherT
              .liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.BRACKETS_GENERATED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(bracketsGeneratedPayload)
              ))
          } yield fightAddedEvents :+ bracketsGeneratedEvent
        }
    } yield events
    eventT.value
  }

  private def validateAndEnrichStages[F[+_]: Monad: IdOperations: EventOperations](
    stages: List[StageDescriptorDTO],
    stageIdMap: Map[String, String],
    categoryId: String,
    state: CompetitionState
  ) = stages.traverse(stage => validateAndEnrichStage[F](stageIdMap, categoryId, state, stage))

  private def validateAndEnrichStage[F[+_]: Monad: IdOperations: EventOperations](
    stageIdMap: Map[String, String],
    categoryId: String,
    state: CompetitionState,
    stage: StageDescriptorDTO
  ) = for {
    duration <- EitherT.fromOption[F](Option(stage.getFightDuration), Errors.InternalError("Fight duration missing"))
    outputSize <- stage.getStageType match {
      case StageType.PRELIMINARY => EitherT.fromOption[F](
          Option(stage.getStageResultDescriptor).map(_.getOutputSize).map(_.toInt),
          Errors.InternalError(s"missing output size for stage $stage")
        )
      case _ => EitherT.fromEither[F](Right(0))
    }
    stageId <- EitherT.fromOption[F](
      stageIdMap.get(stage.getId),
      Errors.InternalError(s"Generated stage id not found in the map for ${stage.getId}")
    )
    groupDescr <- EitherT.liftF(stage.getGroupDescriptors.toList.traverse { it =>
      IdOperations[F].uid.map(id => it.setId(id))
    })
    inputDescriptor = stage.getInputDescriptor.setId(stageId)
      .setSelectors(stage.getInputDescriptor.getSelectors.zipWithIndex.map { case (sel, index) =>
        sel.setId(s"$stageId-s-$index").setApplyToStageId(stageIdMap(sel.getApplyToStageId))
      })
    enrichedOptions = stage.getStageResultDescriptor.getFightResultOptions.toList :+ FightResultOptionDTO.WALKOVER
    enrichedOptionsWithIds <- EitherT.liftF(
      enrichedOptions.map { it =>
        it.setLoserAdditionalPoints(Option(it.getLoserAdditionalPoints).getOrElse(0))
          .setLoserPoints(Option(it.getLoserPoints).getOrElse(0))
          .setWinnerAdditionalPoints(Option(it.getWinnerAdditionalPoints).getOrElse(0))
          .setWinnerPoints(Option(it.getWinnerPoints).getOrElse(0))
      }.traverse { it => IdOperations[F].uid.map(id => it.setId(Option(it.getId).getOrElse(id))) }
    )
    resultDescriptor = stage.getStageResultDescriptor.setId(stageId)
      .setFightResultOptions(enrichedOptionsWithIds.distinctBy { _.getId }.toArray)
    status = if (stage.getStageOrder == 0) StageStatus.WAITING_FOR_APPROVAL else StageStatus.WAITING_FOR_COMPETITORS
    stageWithIds = stage.setCategoryId(categoryId).setId(stageId).setStageStatus(status).setCompetitionId(state.id)
      .setGroupDescriptors(groupDescr.toArray).setInputDescriptor(inputDescriptor)
      .setStageResultDescriptor(resultDescriptor)

    comps =
      if (stage.getStageOrder == 0) {
        state.competitors.map(_.values.toList.filter(_.getCategories.contains(categoryId))).getOrElse(List.empty)
      } else { List.empty }
    size =
      if (stage.getStageOrder == 0) comps.size
      else stage.getInputDescriptor.getNumberOfCompetitors.toInt
    twoFighterFights <- EitherT(
      FightsService.bracketsGenerator[F](state.id, categoryId, stageWithIds, size, duration, comps, outputSize)
        .apply(stageWithIds.getBracketType)
    )
  } yield stageWithIds.setName(Option(stageWithIds.getName).getOrElse("Default brackets")).setStageStatus(status).setInputDescriptor(inputDescriptor).setStageResultDescriptor(stageWithIds.getStageResultDescriptor).setStageStatus(StageStatus.WAITING_FOR_COMPETITORS).setNumberOfFights(twoFighterFights.size) -> twoFighterFights
}
