package compman.compsrv.logic.fight

import cats.{~>, Monad, MonoidK, Show}
import cats.data.State
import cats.free.Free
import cats.implicits._
import compman.compsrv.Utils.groupById
import compservice.model.protobuf.model.{CompetitorStageResult, FightDescription, StageRoundType}

import scala.collection.mutable

object CompetitorSelectionUtils {

  trait Interpreter[F[+_]] {
    def interepret(
      competitorResults: Map[String, Seq[CompetitorStageResult]], // competitorId -> list of stage results
      stageFights: Map[String, FightDescription]
    ): CompetitorSelectA ~> F
  }

  object Interpreter {
    def apply[F[+_]: Monad](implicit F: Interpreter[F]): Interpreter[F] = F

    def asTask[F[+_]: Monad]: Interpreter[F] =
      (competitorResults: Map[String, Seq[CompetitorStageResult]], stagesFights: Map[String, FightDescription]) => {
        def results(stageId: String) = competitorResults.values.flatMap(seq => seq.filter(_.stageId == stageId)).toSeq
        def fights(stageId: String)  = groupById(stagesFights.values.filter(_.stageId == stageId))(_.id)
        new (CompetitorSelectA ~> F) {
          override def apply[A](fa: CompetitorSelectA[A]): F[A] = fa match {
            case FirstNPlaces(stageId, n) => Monad[F]
                .pure(results(stageId).sortBy(_.place).take(n).map(_.competitorId).asInstanceOf[A])
            case LastNPlaces(stageId, n) => Monad[F]
                .pure(results(stageId).sortBy(_.place).takeRight(n).map(_.competitorId).asInstanceOf[A])
            case WinnerOfFight(stageId, id) => for {
                t <- Monad[F].pure(for {
                  fs     <- Some(fights(stageId))
                  f      <- fs.get(id)
                  res    <- f.fightResult
                  winner <- res.winnerId
                } yield Seq(winner).asInstanceOf[A])
              } yield t.getOrElse(Seq.empty)
            case LoserOfFight(stageId, id) => for {
                t <- Monad[F].pure(for {
                  fs     <- Some(fights(stageId))
                  f      <- fs.get(id)
                  res    <- f.fightResult
                  winner <- res.winnerId
                  scores <- Option(f.scores)
                  loser  <- scores.find(!_.competitorId.contains(winner))
                } yield Seq(loser).asInstanceOf[A])
              } yield t.getOrElse(Seq.empty)

            case PassedToRound(stageId, n, roundType) => for {
                t <- Monad[F].pure(for {
                  fs <- Some(fights(stageId))
                  filtered = fs.values.filter(_.roundType == roundType).filter(_.numberInRound == n)
                  ids      = filtered.flatMap(_.scores.map(_.competitorId))
                } yield ids.toSeq.asInstanceOf[A])
              } yield t.getOrElse(Seq.empty)

            case Return(ids) => Monad[F].pure(ids).map(_.asInstanceOf[A])
          }
        }
      }

    type Print[_] = State[mutable.StringBuilder, _]

    implicit val show: Show[CompetitorSelectA[_]] = {
      case FirstNPlaces(stageId, n)             => s"First $n places of stage $stageId"
      case LastNPlaces(stageId, n)              => s"Last $n places of stage $stageId"
      case WinnerOfFight(stageId, id)           => s"Winner of fight $id of stage $stageId"
      case LoserOfFight(stageId, id)            => s"Loser of fight $id of stage $stageId"
      case PassedToRound(stageId, n, roundType) => s"Passed to round $n of type $roundType of stage $stageId"
      case Return(ids)                          => s"Selected: ${ids.show}"
    }

    def log: CompetitorSelectA ~> Print = new (CompetitorSelectA ~> Print) {
      override def apply[A](fa: CompetitorSelectA[A]): Print[A] = { State(s => (s.append(Show(show).show(fa)), ())) }
    }

  }

  private[fight] sealed trait CompetitorSelectA[A]
  private[fight] case class FirstNPlaces(stageId: String, n: Int)      extends CompetitorSelectA[Seq[String]]
  private[fight] case class LastNPlaces(stageId: String, n: Int)       extends CompetitorSelectA[Seq[String]]
  private[fight] case class WinnerOfFight(stageId: String, id: String) extends CompetitorSelectA[Seq[String]]
  private[fight] case class LoserOfFight(stageId: String, id: String)  extends CompetitorSelectA[Seq[String]]
  private[fight] case class PassedToRound(stageId: String, n: Int, roundType: StageRoundType)
      extends CompetitorSelectA[Seq[String]]
  private[fight] case class Return(ids: Seq[String]) extends CompetitorSelectA[Seq[String]]

  type CompetitorSelect[A] = Free[CompetitorSelectA, A]

  private[fight] def firstNPlaces(stageId: String, n: Int): CompetitorSelect[Seq[String]] = Free
    .liftF(FirstNPlaces(stageId, n))
  private[fight] def lastNPlaces(stageId: String, n: Int): CompetitorSelect[Seq[String]] = Free
    .liftF(LastNPlaces(stageId, n))
  private[fight] def winnerOfFight(stageId: String, id: String): CompetitorSelect[Seq[String]] = Free
    .liftF(WinnerOfFight(stageId, id))
  private[fight] def loserOfFight(stageId: String, id: String): CompetitorSelect[Seq[String]] = Free
    .liftF(LoserOfFight(stageId, id))
  private[fight] def passedToRound(stageId: String, n: Int, roundType: StageRoundType): CompetitorSelect[Seq[String]] =
    Free.liftF(PassedToRound(stageId, n, roundType))
  private[fight] def returnIds(ids: Seq[String]): CompetitorSelect[Seq[String]] = Free.liftF(Return(ids))
  private[fight] def and[F[_]: MonoidK, A](
    a: CompetitorSelect[F[A]],
    b: CompetitorSelect[F[A]]
  ): CompetitorSelect[F[A]] = for {
    l <- a
    r <- b
  } yield MonoidK[F].combineK(l, r)
}
