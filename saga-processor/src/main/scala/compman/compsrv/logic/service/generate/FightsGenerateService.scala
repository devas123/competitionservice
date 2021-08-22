package compman.compsrv.logic.service.generate

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.service.FightsService._
import compman.compsrv.model.Errors
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CompetitorDTO, FightDescriptionDTO}

object FightsGenerateService {
  def bracketsGenerator[F[+_]: Monad: CompetitionStateOperations](
    competitionId: String,
    categoryId: String,
    stage: StageDescriptorDTO,
    compssize: Int,
    duration: BigDecimal,
    competitors: List[CompetitorDTO],
    outputSize: Int
  ): PartialFunction[BracketType, F[CanFail[List[FightDescriptionDTO]]]] = {

    import Brackets._
    import Groups._
    def postProcessFights(generated: List[FightDescriptionDTO]) = {
      val fights = generated.groupMapReduce(_.getId)(identity)((a, _) => a)
      val lifted: EitherT[F, Errors.Error, List[FightDescriptionDTO]] = for {
        assignedFights <- stage.getStageOrder.toInt match {
          case 0 => EitherT.fromEither[F](Brackets.distributeCompetitors(competitors, fights, stage.getBracketType))
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
          res <- postProcessFights(generated)
        } yield res).value
      case BracketType.DOUBLE_ELIMINATION => (for {
          generated <-
            EitherT(generateDoubleEliminationBracket[F](competitionId, categoryId, stage.getId, compssize, duration))
          res <- postProcessFights(generated)
        } yield res).value
      case BracketType.GROUP => (for {
          generated   <- EitherT(generateStageFights(competitionId, categoryId, stage, duration, competitors))
          distributed <- EitherT.fromEither[F](Groups.distributeCompetitors(competitors, generated))
        } yield distributed).value
    }
  }
}
