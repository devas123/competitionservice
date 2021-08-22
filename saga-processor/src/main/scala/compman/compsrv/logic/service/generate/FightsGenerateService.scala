package compman.compsrv.logic.service.generate

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.service.FightsService._
import compman.compsrv.model.Errors
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CompetitorDTO, FightDescriptionDTO}

trait FightsGenerateService {
  def generateStageFights(
    competitionId: String,
    categoryId: String,
    stage: StageDescriptorDTO,
    compssize: Int,
    duration: BigDecimal,
    competitors: List[CompetitorDTO],
    outputSize: Int
  ): PartialFunction[BracketType, CanFail[List[FightDescriptionDTO]]]
}

object FightsGenerateService {
  def bracketsGenerator[F[_]: Monad: CompetitionStateOperations](
    competitionId: String,
    categoryId: String,
    stage: StageDescriptorDTO,
    compssize: Int,
    duration: BigDecimal,
    competitors: List[CompetitorDTO],
    outputSize: Int
  ): PartialFunction[BracketType, F[Either[Errors.Error, List[FightDescriptionDTO]]]] = {

    import Brackets._
    def postProcessFights(generated: List[FightDescriptionDTO]) = {
      val fights = generated.groupMapReduce(_.getId)(identity)((a, _) => a)
      val lifted: EitherT[F, Errors.Error, List[FightDescriptionDTO]] = for {
        assignedFights <- stage.getStageOrder.toInt match {
          case 0 => EitherT.fromEither[F](distributeCompetitors(competitors, fights, stage.getBracketType))
          case _ => EitherT.fromEither[F](Right(fights))
        }
        markedUncompletableFights <- EitherT
          .liftF[F, Errors.Error, List[FightDescriptionDTO]](markAndProcessUncompletableFights[F](assignedFights))
        res <-
          if (stage.getStageType == StageType.PRELIMINARY) {
            EitherT.liftF[F, Errors.Error, List[FightDescriptionDTO]](
              filterPreliminaryFights[F](outputSize, markedUncompletableFights, stage.getBracketType)
            )
          } else { EitherT.fromEither[F](Right[Errors.Error, List[FightDescriptionDTO]](markedUncompletableFights)) }
      } yield res
      lifted
    }

    {
      case BracketType.SINGLE_ELIMINATION => (for {
          generated <- EitherT.fromEither[F] {
            if (stage.getHasThirdPlaceFight) {
              for {
                fights <-
                  generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stage.getId, compssize, duration)
                res <- generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, stage.getId, fights)
              } yield res
            } else { generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stage.getId, compssize, duration) }
          }
          res <- postProcessFights(generated)
        } yield res).value
      case BracketType.DOUBLE_ELIMINATION => (for {
          generated <- EitherT.fromEither[F](
            generateDoubleEliminationBracket(competitionId, categoryId, stage.getId, compssize, duration)
          )
          res <- postProcessFights(generated)
        } yield res).value
    }
  }
}
