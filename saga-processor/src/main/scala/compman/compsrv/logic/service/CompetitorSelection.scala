package compman.compsrv.logic.service

import cats.{~>, MonoidK, Show}
import cats.free.Free
import cats.implicits.toShow
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.CompetitionState
import zio.Task

object CompetitorSelection {
  sealed trait CompetitorSelectA[A]
  case class FirstNPlaces(stageId: String, n: Int)      extends CompetitorSelectA[Seq[String]]
  case class LastNPlaces(stageId: String, n: Int)       extends CompetitorSelectA[Seq[String]]
  case class WinnerOfFight(stageId: String, id: String) extends CompetitorSelectA[Seq[String]]
  case class LoserOfFight(stageId: String, id: String)  extends CompetitorSelectA[Seq[String]]
  case class PassedToRound(stageId: String, n: Int, roundType: StageRoundType)
      extends CompetitorSelectA[Seq[String]]
  case class Return(ids: Seq[String]) extends CompetitorSelectA[Seq[String]]

  type CompetitorSelect[A] = Free[CompetitorSelectA, A]

  def firstNPlaces(stageId: String, n: Int): CompetitorSelect[Seq[String]] = Free
    .liftF(FirstNPlaces(stageId, n))
  def lastNPlaces(stageId: String, n: Int): CompetitorSelect[Seq[String]] = Free
    .liftF(LastNPlaces(stageId, n))
  def winnerOfFight(stageId: String, id: String): CompetitorSelect[Seq[String]] = Free
    .liftF(WinnerOfFight(stageId, id))
  def loserOfFight(stageId: String, id: String): CompetitorSelect[Seq[String]] = Free
    .liftF(LoserOfFight(stageId, id))
  def passedToRound(
      stageId: String,
      n: Int,
      roundType: StageRoundType
  ): CompetitorSelect[Seq[String]] = Free.liftF(PassedToRound(stageId, n, roundType))
  def returnIds(ids: Seq[String]): CompetitorSelect[Seq[String]] = Free.liftF(Return(ids))
  def and[F[_]: MonoidK, A](
      a: CompetitorSelect[F[A]],
      b: CompetitorSelect[F[A]]
  ): CompetitorSelect[F[A]] =
    for {
      l <- a
      r <- b
    } yield MonoidK[F].combineK(l, r)

  def log: CompetitorSelectA ~> Show =
    new (CompetitorSelectA ~> Show) {
      override def apply[A](fa: CompetitorSelectA[A]): Show[A] = {
        fa match {
          case FirstNPlaces(stageId, n) =>
            Show.show(_ => s"First $n places of stage $stageId")
          case LastNPlaces(stageId, n) =>
            Show.show(_ => s"Last $n places of stage $stageId")
          case WinnerOfFight(stageId, id) =>
            Show.show(_ => s"Winner of fight $id of stage $stageId")
          case LoserOfFight(stageId, id) =>
            Show.show(_ => s"Loser of fight $id of stage $stageId")
          case PassedToRound(stageId, n, roundType) =>
            Show.show(_ => s"Passed to round $n of type $roundType of stage $stageId")
          case Return(ids) =>
            Show.show(_ => s"Selected: ${ids.show}")
        }
      }
    }

  def asTask(state: CompetitionState): CompetitorSelectA ~> Task = {
    val stages = state.stages.getOrElse(Map.empty)
    def results(stageId: String) = stages
      .get(stageId)
      .flatMap(s => Option(s.getStageResultDescriptor))
      .flatMap(s => Option(s.getCompetitorResults))
      .map(res => res.groupMapReduce(_.getCompetitorId)(identity)((a, _) => a))
      .getOrElse(Map.empty)
    def fights(stageId: String) = state
      .fights
      .map(_.values.filter(_.getStageId == stageId).groupMapReduce(_.getId)(identity)((a, _) => a))
    new (CompetitorSelectA ~> Task) {
      override def apply[A](fa: CompetitorSelectA[A]): Task[A] = {
        fa match {
          case FirstNPlaces(stageId, n) =>
            Task(
              results(stageId)
                .values
                .toSeq
                .sortBy(_.getPlace)
                .take(n)
                .map(_.getCompetitorId)
                .asInstanceOf[A]
            )

          case LastNPlaces(stageId, n) =>
            Task(
              results(stageId)
                .values
                .toSeq
                .sortBy(_.getPlace)
                .takeRight(n)
                .map(_.getCompetitorId)
                .asInstanceOf[A]
            )
          case WinnerOfFight(stageId, id) =>
            for {
              t <- Task(
                for {
                  fs     <- fights(stageId)
                  f      <- fs.get(id)
                  res    <- Option(f.getFightResult)
                  winner <- Option(res.getWinnerId)
                } yield Seq(winner).asInstanceOf[A]
              )
            } yield t.getOrElse(Seq.empty)
          case LoserOfFight(stageId, id) =>
            for {
              t <- Task(
                for {
                  fs     <- fights(stageId)
                  f      <- fs.get(id)
                  res    <- Option(f.getFightResult)
                  winner <- Option(res.getWinnerId)
                  scores <- Option(f.getScores)
                  loser  <- scores.find(_.getCompetitorId != winner)
                } yield Seq(loser).asInstanceOf[A]
              )
            } yield t.getOrElse(Seq.empty)

          case PassedToRound(stageId, n, roundType) =>
            for {
              t <- Task(
                for {
                  fs <- fights(stageId)
                  filtered = fs
                    .values
                    .filter(_.getRoundType == roundType)
                    .filter(_.getNumberInRound == n)
                  ids = filtered.flatMap(_.getScores.map(_.getCompetitorId))
                } yield ids.toSeq.asInstanceOf[A]
              )
            } yield t.getOrElse(Seq.empty)

          case Return(ids) =>
            Task(ids).map(_.asInstanceOf[A])
        }
      }
    }
  }

  /*
  def asSequence(state: CompetitionState): CompetitorSelectA ~> Option = {
    val stages = getStages(state)
    def results(stageId: String) = stages
      .get(stageId)
      .flatMap(s => Option(s.getStageResultDescriptor))
      .flatMap(s => Option(s.getCompetitorResults))
      .map(res => res.groupMapReduce(_.getCompetitorId)(identity)((a, _) => a))
      .getOrElse(Map.empty)
    def fights(stageId: String) = state
      .fights
      .map(_.filter(_.getStageId == stageId).groupMapReduce(_.getId)(identity)((a, _) => a))
    new (CompetitorSelectA ~> Option) {
      override def apply[A](fa: CompetitorSelectA[A]): Option[A] = {
        fa match {
          case FirstNPlaces(stageId, n) =>
            Option(
              results(stageId)
                .values
                .toSeq
                .sortBy(_.getPlace)
                .take(n)
                .map(_.getCompetitorId)
                .asInstanceOf[A]
            )

          case LastNPlaces(stageId, n) =>
            Option(
              results(stageId)
                .values
                .toSeq
                .sortBy(_.getPlace)
                .takeRight(n)
                .map(_.getCompetitorId)
                .asInstanceOf[A]
            )
          case WinnerOfFight(stageId, id) =>
            for {
              fs     <- fights(stageId)
              f      <- fs.get(id)
              res    <- Option(f.getFightResult)
              winner <- Option(res.getWinnerId)
            } yield Seq(winner).asInstanceOf[A]
          case LoserOfFight(stageId, id) =>
              for {
                fs     <- fights(stageId)
                f      <- fs.get(id)
                res    <- Option(f.getFightResult)
                winner <- Option(res.getWinnerId)
                scores <- Option(f.getScores)
                loser  <- scores.find(_.getCompetitorId != winner)
              } yield Seq(loser).asInstanceOf[A]
          case PassedToRound(stageId, n, roundType) =>
              for {
                fs <- fights(stageId)
                filtered = fs
                  .values
                  .filter(_.getRoundType == roundType)
                  .filter(_.getNumberInRound == n)
                ids = filtered.flatMap(_.getScores.map(_.getCompetitorId))
              } yield ids.toSeq.asInstanceOf[A]
          case Return(ids) =>
            Option(ids).map(_.asInstanceOf[A])
        }
      }
    }
  }
   */
}
