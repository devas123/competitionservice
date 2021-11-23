package compman.compsrv.logic.fight

import cats.{Monad, MonoidK, Show, ~>}
import cats.free.Free
import cats.implicits._
import compman.compsrv.Utils.groupById
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.brackets.StageRoundType
import zio.Task

object CompetitorSelectionUtils {

  trait Interpreter[F[+_]] {
    def interepret(state: CompetitionState): CompetitorSelectA ~> F
  }

  object Interpreter {
    def apply[F[+_]: Monad](implicit F: Interpreter[F]): Interpreter[F] = F

    val asTask: Interpreter[LIO] = (state: CompetitionState) => {
      val stages = state.stages.getOrElse(Map.empty)
      def results(stageId: String) = stages.get(stageId).flatMap(s => Option(s.getStageResultDescriptor))
        .flatMap(s => Option(s.getCompetitorResults))
        .map(res => groupById(res)(_.getCompetitorId)).getOrElse(Map.empty)
      def fights(stageId: String) = state.fights
        .map(fs => groupById(fs.values.filter(_.getStageId == stageId))(_.getId))
      new (CompetitorSelectA ~> LIO) {
        override def apply[A](fa: CompetitorSelectA[A]): LIO[A] = {
          fa match {
            case FirstNPlaces(stageId, n) =>
              Task(results(stageId).values.toSeq.sortBy(_.getPlace).take(n).map(_.getCompetitorId).asInstanceOf[A])

            case LastNPlaces(stageId, n) =>
              Task(results(stageId).values.toSeq.sortBy(_.getPlace).takeRight(n).map(_.getCompetitorId).asInstanceOf[A])
            case WinnerOfFight(stageId, id) => for {
              t <- Task(for {
                fs     <- fights(stageId)
                f      <- fs.get(id)
                res    <- Option(f.getFightResult)
                winner <- Option(res.getWinnerId)
              } yield Seq(winner).asInstanceOf[A])
            } yield t.getOrElse(Seq.empty)
            case LoserOfFight(stageId, id) => for {
              t <- Task(for {
                fs     <- fights(stageId)
                f      <- fs.get(id)
                res    <- Option(f.getFightResult)
                winner <- Option(res.getWinnerId)
                scores <- Option(f.getScores)
                loser  <- scores.find(_.getCompetitorId != winner)
              } yield Seq(loser).asInstanceOf[A])
            } yield t.getOrElse(Seq.empty)

            case PassedToRound(stageId, n, roundType) => for {
              t <- Task(for {
                fs <- fights(stageId)
                filtered = fs.values.filter(_.getRoundType == roundType).filter(_.getNumberInRound == n)
                ids      = filtered.flatMap(_.getScores.map(_.getCompetitorId))
              } yield ids.toSeq.asInstanceOf[A])
            } yield t.getOrElse(Seq.empty)

            case Return(ids) => Task(ids).map(_.asInstanceOf[A])
          }
        }
      }
    }
  }

  private [fight] sealed trait CompetitorSelectA[A]
  private [fight] case class FirstNPlaces(stageId: String, n: Int)                             extends CompetitorSelectA[Seq[String]]
  private [fight] case class LastNPlaces(stageId: String, n: Int)                              extends CompetitorSelectA[Seq[String]]
  private [fight] case class WinnerOfFight(stageId: String, id: String)                        extends CompetitorSelectA[Seq[String]]
  private [fight] case class LoserOfFight(stageId: String, id: String)                         extends CompetitorSelectA[Seq[String]]
  private [fight] case class PassedToRound(stageId: String, n: Int, roundType: StageRoundType) extends CompetitorSelectA[Seq[String]]
  private [fight] case class Return(ids: Seq[String])                                          extends CompetitorSelectA[Seq[String]]

  type CompetitorSelect[A] = Free[CompetitorSelectA, A]

  private [fight] def firstNPlaces(stageId: String, n: Int): CompetitorSelect[Seq[String]]      = Free.liftF(FirstNPlaces(stageId, n))
  private [fight] def lastNPlaces(stageId: String, n: Int): CompetitorSelect[Seq[String]]       = Free.liftF(LastNPlaces(stageId, n))
  private [fight] def winnerOfFight(stageId: String, id: String): CompetitorSelect[Seq[String]] = Free.liftF(WinnerOfFight(stageId, id))
  private [fight] def loserOfFight(stageId: String, id: String): CompetitorSelect[Seq[String]]  = Free.liftF(LoserOfFight(stageId, id))
  private [fight] def passedToRound(stageId: String, n: Int, roundType: StageRoundType): CompetitorSelect[Seq[String]] = Free
    .liftF(PassedToRound(stageId, n, roundType))
  private [fight] def returnIds(ids: Seq[String]): CompetitorSelect[Seq[String]] = Free.liftF(Return(ids))
  private [fight] def and[F[_]: MonoidK, A](a: CompetitorSelect[F[A]], b: CompetitorSelect[F[A]]): CompetitorSelect[F[A]] = for {
    l <- a
    r <- b
  } yield MonoidK[F].combineK(l, r)

  private [fight] def log: CompetitorSelectA ~> Show = new (CompetitorSelectA ~> Show) {
    override def apply[A](fa: CompetitorSelectA[A]): Show[A] = {
      fa match {
        case FirstNPlaces(stageId, n)   => Show.show(_ => s"First $n places of stage $stageId")
        case LastNPlaces(stageId, n)    => Show.show(_ => s"Last $n places of stage $stageId")
        case WinnerOfFight(stageId, id) => Show.show(_ => s"Winner of fight $id of stage $stageId")
        case LoserOfFight(stageId, id)  => Show.show(_ => s"Loser of fight $id of stage $stageId")
        case PassedToRound(stageId, n, roundType) => Show
            .show(_ => s"Passed to round $n of type $roundType of stage $stageId")
        case Return(ids) => Show.show(_ => s"Selected: ${ids.show}")
      }
    }
  }

}
