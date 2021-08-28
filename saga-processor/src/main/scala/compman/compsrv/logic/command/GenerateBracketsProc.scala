package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.service.fights.{zero, FightsGenerateService}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, GenerateBracketsCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.{NoCategoryIdError, NoCompetitionIdError, NoPayloadError}
import compman.compsrv.model.dto.brackets.{FightResultOptionDTO, StageStatus, StageType}
import compman.compsrv.model.events.payload.{BracketsGeneratedPayload, FightsAddedToStagePayload}

import java.util.UUID

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
      payload       <- EitherT.fromOption[F](command.payload, NoPayloadError())
      categoryId    <- EitherT.fromOption[F](command.categoryId, NoCategoryIdError())
      competitionId <- EitherT.fromOption[F](command.competitionId, NoCompetitionIdError())
      exists = state.categories.exists(_.contains(categoryId))
      events <-
        if (!exists) {
          EitherT.fromEither[F](Left[Errors.Error, List[EventDTO]](Errors.CategoryDoesNotExist(
            command.categoryId.map(Array(_)).getOrElse(Array.empty)
          )))
        } else {
          val stages     = payload.getStageDescriptors.toList.sortBy(_.getStageOrder)
          val stageIdMap = stages.groupMapReduce(_.getId)(_ => UUID.randomUUID().toString)((a, _) => a)
          for {
            updatedStages <- stages.traverse { stage =>
              for {
                duration <- EitherT
                  .fromOption[F](Option(stage.getFightDuration), Errors.InternalError("Fight duration missing"))
                outputSize <- stage.getStageType match {
                  case StageType.PRELIMINARY => EitherT.fromOption[F](
                      Option(stage.getStageResultDescriptor).map(_.getOutputSize).map(_.toInt),
                      Errors.InternalError(s"missing output size for stage $stage")
                    )
                  case _ => EitherT.fromEither[F](Right(0))
                }
                stageId <- EitherT.fromOption[F](
                  stageIdMap.get(stage.getId),
                  Errors.InternalError("Generated stage id not found in the map.")
                )
                groupDescr = stage.getGroupDescriptors.map { it => it.setId(UUID.randomUUID().toString) }
                inputDescriptor = stage.getInputDescriptor.setId(stageId)
                  .setSelectors(stage.getInputDescriptor.getSelectors.zipWithIndex.map { case (sel, index) =>
                    sel.setId(s"$stageId-s-$index").setApplyToStageId(stageIdMap(sel.getApplyToStageId))
                  })
                enrichedOptions = stage.getStageResultDescriptor.getFightResultOptions.toList :+
                  FightResultOptionDTO.WALKOVER
                resultDescriptor = stage.getStageResultDescriptor.setId(stageId)
                  .setFightResultOptions(enrichedOptions.map { it =>
                    it.setId(Option(it.getId).getOrElse(UUID.randomUUID().toString))
                      .setLoserAdditionalPoints(Option(it.getLoserAdditionalPoints).getOrElse(0))
                      .setLoserPoints(Option(it.getLoserPoints).getOrElse(0))
                      .setWinnerAdditionalPoints(Option(it.getWinnerAdditionalPoints).getOrElse(0))
                      .setWinnerPoints(Option(it.getWinnerPoints).getOrElse(0))
                  }.distinctBy { _.getId }.toArray)
                status =
                  if (stage.getStageOrder == 0) StageStatus.WAITING_FOR_APPROVAL
                  else StageStatus.WAITING_FOR_COMPETITORS
                stageWithIds = stage.setCategoryId(categoryId).setId(stageId).setStageStatus(status)
                  .setCompetitionId(competitionId).setGroupDescriptors(groupDescr).setInputDescriptor(inputDescriptor)
                  .setStageResultDescriptor(resultDescriptor)
                size =
                  if (stage.getStageOrder == 0) state.competitors.map(_.size).getOrElse(0)
                  else stage.getInputDescriptor.getNumberOfCompetitors.toInt
                comps =
                  if (stage.getStageOrder == 0) { state.competitors.map(_.values.toList).getOrElse(List.empty) }
                  else { List.empty }
                twoFighterFights <- EitherT(
                  FightsGenerateService
                    .bracketsGenerator[F](competitionId, categoryId, stageWithIds, size, duration, comps, outputSize)
                    .apply(stageWithIds.getBracketType)
                )
              } yield stageWithIds.setName(Option(stageWithIds.getName).getOrElse("Default brackets"))
                .setStageStatus(status).setInputDescriptor(inputDescriptor)
                .setStageResultDescriptor(stageWithIds.getStageResultDescriptor)
                .setStageStatus(StageStatus.WAITING_FOR_COMPETITORS).setNumberOfFights(twoFighterFights.size) ->
                twoFighterFights
            }
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
}
