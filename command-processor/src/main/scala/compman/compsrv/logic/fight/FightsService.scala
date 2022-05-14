package compman.compsrv.logic.fight

import cats.Monad
import cats.data.EitherT
import compman.compsrv.Utils.groupById
import compman.compsrv.logic.fight.FightUtils.filterPreliminaryFights
import compman.compsrv.model.Errors
import compservice.model.protobuf.model._


object FightsService {

  def distributeCompetitors[F[+_]: Monad](
    competitors: List[Competitor],
    fights: Map[String, FightDescription]
  ): PartialFunction[BracketType, F[CanFail[List[FightDescription]]]] = {
    case BracketType.DOUBLE_ELIMINATION | BracketType.SINGLE_ELIMINATION => Monad[F]
        .pure { BracketsUtils.distributeCompetitors(competitors, fights).map(_.values.toList) }
    case BracketType.GROUP => Monad[F].pure { GroupsUtils.distributeCompetitors(competitors, fights.values.toList) }
  }

  def buildStageResult[F[+_]: Monad](
    stageStatus: StageStatus,
    stageType: StageType,
    fights: List[FightDescription],
    stageId: String,
    fightResultOptions: Option[List[FightResultOption]]
  ): PartialFunction[BracketType, F[CanFail[List[CompetitorStageResult]]]] = {
    case BracketType.DOUBLE_ELIMINATION => Monad[F].pure {
        BracketsUtils.buildStageResults(BracketType.DOUBLE_ELIMINATION, stageStatus, stageType, fights, stageId)
      }
    case BracketType.SINGLE_ELIMINATION => Monad[F].pure {
        BracketsUtils.buildStageResults(BracketType.SINGLE_ELIMINATION, stageStatus, stageType, fights, stageId)
      }
    case BracketType.GROUP => Monad[F]
        .pure { GroupsUtils.buildStageResults(stageStatus, fights, stageId, fightResultOptions.get) }
  }

  def bracketsGenerator[F[+_]: Monad](
                                       competitionId: String,
                                       categoryId: String,
                                       stage: StageDescriptor,
                                       targetCompetitorsSize: Int,
                                       durationSeconds: Int,
                                       competitorsToDistribute: List[Competitor],
                                       outputSize: Int
  ): PartialFunction[BracketType, F[CanFail[List[FightDescription]]]] = {

    import BracketsUtils._
    import GroupsUtils._
    def postProcessFights(generated: List[FightDescription]) = {
      val fights = groupById(generated)(_.id)
      val lifted: EitherT[F, Errors.Error, List[FightDescription]] = for {
        assignedFights <- stage.stageOrder match {
          case 0 => EitherT.fromEither[F](BracketsUtils.distributeCompetitors(competitorsToDistribute, fights))
          case _ => EitherT.fromEither[F](Right(fights))
        }
        markedUncompletableFights <- EitherT.liftF[F, Errors.Error, Map[String, FightDescription]](
          FightUtils.markAndProcessUncompletableFights[F](assignedFights)
        )
        res <-
          if (stage.stageType == StageType.PRELIMINARY) {
            EitherT.liftF[F, Errors.Error, List[FightDescription]](
              filterPreliminaryFights[F](outputSize, markedUncompletableFights.values.toList, stage.bracketType)
            )
          } else {
            EitherT
              .fromEither[F](Right[Errors.Error, List[FightDescription]](markedUncompletableFights.values.toList))
          }
      } yield res
      lifted
    }

    {
      case BracketType.SINGLE_ELIMINATION => (for {
          generated <-
            if (stage.hasThirdPlaceFight) {
              for {
                fights <- EitherT(generateEmptyWinnerRoundsForCategory[F](
                  competitionId,
                  categoryId,
                  stage.id,
                  targetCompetitorsSize,
                  durationSeconds
                ))
                res <- EitherT.fromEither[F](
                  generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, stage.id, fights)
                )
              } yield res
            } else {
              for {
                res <- EitherT(generateEmptyWinnerRoundsForCategory[F](
                  competitionId,
                  categoryId,
                  stage.id,
                  targetCompetitorsSize,
                  durationSeconds
                ))
              } yield res
            }
          res <-
            if (stage.stageOrder == 0) postProcessFights(generated) else EitherT.pure[F, Errors.Error](generated)
        } yield res).value
      case BracketType.DOUBLE_ELIMINATION => (for {
          generated <- EitherT(
            generateDoubleEliminationBracket[F](competitionId, categoryId, stage.id, targetCompetitorsSize, durationSeconds)
          )
          res <-
            if (stage.stageOrder == 0) postProcessFights(generated) else EitherT.pure[F, Errors.Error](generated)
        } yield res).value
      case BracketType.GROUP => (for {
          generated <- EitherT(generateStageFights(competitionId, categoryId, stage, durationSeconds, competitorsToDistribute))
        } yield generated).value
    }
  }
}
