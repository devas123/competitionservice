package compman.compsrv.logic.fight

import cats.Monad
import cats.data.EitherT
import compman.compsrv.Utils.groupById
import compman.compsrv.logic.fight.FightUtils.filterPreliminaryFights
import compman.compsrv.model.Errors
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CompetitorDTO, FightDescriptionDTO}

object FightsService {

  def distributeCompetitors[F[+_]: Monad](
    competitors: List[CompetitorDTO],
    fights: Map[String, FightDescriptionDTO]
  ): PartialFunction[BracketType, F[CanFail[List[FightDescriptionDTO]]]] = {
    case BracketType.DOUBLE_ELIMINATION | BracketType.SINGLE_ELIMINATION => Monad[F]
        .pure { BracketsUtils.distributeCompetitors(competitors, fights).map(_.values.toList) }
    case BracketType.GROUP => Monad[F].pure { GroupsUtils.distributeCompetitors(competitors, fights.values.toList) }
  }

  def buildStageResult[F[+_]: Monad](
    stageStatus: StageStatus,
    stageType: StageType,
    fights: List[FightDescriptionDTO],
    stageId: String,
    fightResultOptions: Option[List[FightResultOptionDTO]]
  ): PartialFunction[BracketType, F[CanFail[List[CompetitorStageResultDTO]]]] = {
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
    stage: StageDescriptorDTO,
    compssize: Int,
    duration: BigDecimal,
    competitors: List[CompetitorDTO],
    outputSize: Int
  ): PartialFunction[BracketType, F[CanFail[List[FightDescriptionDTO]]]] = {

    import BracketsUtils._
    import GroupsUtils._
    def postProcessFights(generated: List[FightDescriptionDTO]) = {
      val fights = groupById(generated)(_.getId)
      val lifted: EitherT[F, Errors.Error, List[FightDescriptionDTO]] = for {
        assignedFights <- stage.getStageOrder.toInt match {
          case 0 => EitherT.fromEither[F](BracketsUtils.distributeCompetitors(competitors, fights))
          case _ => EitherT.fromEither[F](Right(fights))
        }
        markedUncompletableFights <- EitherT.liftF[F, Errors.Error, Map[String, FightDescriptionDTO]](
          FightUtils.markAndProcessUncompletableFights[F](assignedFights)
        )
        res <-
          if (stage.getStageType == StageType.PRELIMINARY) {
            EitherT.liftF[F, Errors.Error, List[FightDescriptionDTO]](
              filterPreliminaryFights[F](outputSize, markedUncompletableFights.values.toList, stage.getBracketType)
            )
          } else {
            EitherT
              .fromEither[F](Right[Errors.Error, List[FightDescriptionDTO]](markedUncompletableFights.values.toList))
          }
      } yield res
      lifted
    }

    {
      case BracketType.SINGLE_ELIMINATION => (for {
          generated <-
            if (stage.getHasThirdPlaceFight) {
              for {
                fights <- EitherT(
                  generateEmptyWinnerRoundsForCategory[F](competitionId, categoryId, stage.getId, compssize, duration)
                )
                res <- EitherT.fromEither[F](
                  generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, stage.getId, fights)
                )
              } yield res
            } else {
              for {
                res <- EitherT(
                  generateEmptyWinnerRoundsForCategory[F](competitionId, categoryId, stage.getId, compssize, duration)
                )
              } yield res
            }
          res <- if (stage.getStageOrder == 0) postProcessFights(generated) else EitherT.pure[F, Errors.Error](generated)
        } yield res).value
      case BracketType.DOUBLE_ELIMINATION => (for {
          generated <-
            EitherT(generateDoubleEliminationBracket[F](competitionId, categoryId, stage.getId, compssize, duration))
          res <- if (stage.getStageOrder == 0) postProcessFights(generated) else EitherT.pure[F, Errors.Error](generated)
        } yield res).value
      case BracketType.GROUP => (for {
          generated <- EitherT(generateStageFights(competitionId, categoryId, stage, duration, competitors))
        } yield generated).value
    }
  }
}
