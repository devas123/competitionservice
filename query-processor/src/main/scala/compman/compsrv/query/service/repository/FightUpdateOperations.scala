package compman.compsrv.query.service.repository

import cats.implicits._
import cats.Monad
import cats.effect.IO
import compman.compsrv.query.model._
import compservice.model.protobuf.model.FightStatus
import org.mongodb.scala.MongoClient
import org.mongodb.scala.model.UpdateOneModel

import java.util.Date
import java.util.concurrent.atomic.AtomicReference

trait FightUpdateOperations[F[+_]] {
  def addFight(fight: Fight): F[Unit]
  def addFights(fights: List[Fight]): F[Unit]
  def updateFight(fight: Fight): F[Unit]
  def updateFightScoresAndResultAndStatus(
    competitionId: String
  )(fightId: String, scores: List[CompScore], fightResult: FightResult, status: FightStatus): F[Unit]
  def updateFightScores(fights: List[Fight]): F[Unit]
  def updateFightStartTime(fights: List[FightStartTimeUpdate]): F[Unit]
  def updateFightOrderAndMat(updates: List[FightOrderUpdateExtended]): F[Unit]
  def removeFight(competitionId: String)(id: String): F[Unit]
  def removeFights(competitionId: String)(ids: List[String]): F[Unit]
  def removeFightsForCategory(competitionId: String)(categoryId: String): F[Unit]
  def removeFightsForCompetition(competitionId: String): F[Unit]
}

object FightUpdateOperations {
  def apply[F[+_]](implicit F: FightUpdateOperations[F]): FightUpdateOperations[F] = F

  def test[F[_]: Monad](fights: Option[AtomicReference[Map[String, Fight]]] = None): FightUpdateOperations[F] =
    new FightUpdateOperations[F] with CommonTestOperations {
      override def removeFightsForCategory(competitionId: String)(categoryId: String): F[Unit] = Monad[F]
        .pure(fights.foreach(_.updateAndGet(fs => fs.filter(f => f._2.categoryId != categoryId))))

      override def updateFightStartTime(fights: List[FightStartTimeUpdate]): F[Unit] = updateFightScores(List.empty)
      override def addFight(fight: Fight): F[Unit] = add(fights)(fight.id)(Some(fight))

      override def addFights(fights: List[Fight]): F[Unit] = fights.traverse(addFight).map(_ => ())

      override def updateFight(fight: Fight): F[Unit] = update(fights)(fight.id)(_ => fight)

      override def updateFightScores(fights: List[Fight]): F[Unit] = fights.traverse(updateFight).map(_ => ())

      override def removeFight(competitionId: String)(id: String): F[Unit] = remove(fights)(id)

      override def removeFights(competitionId: String)(ids: List[String]): F[Unit] = ids
        .traverse(removeFight(competitionId)).map(_ => ())

      override def removeFightsForCompetition(competitionId: String): F[Unit] = Monad[F]
        .pure(fights.foreach(_.updateAndGet(_.filter(_._2.competitionId != competitionId))))

      override def updateFightScoresAndResultAndStatus(
        competitionId: String
      )(fightId: String, scores: List[CompScore], fightResult: FightResult, status: FightStatus): F[Unit] = update(
        fights
      )(fightId)(f => f.copy(scores = scores, fightResult = Option(fightResult), status = Option(status)))

      override def updateFightOrderAndMat(updates: List[FightOrderUpdateExtended]): F[Unit] =
        updateFightScores(List.empty)

    }

  def live(mongo: MongoClient, name: String): FightUpdateOperations[IO] = new FightUpdateOperations[IO]
    with CommonLiveOperations with FightFieldsAndFilters {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._

    override def addFight(fight: Fight): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.insertOne(fight)
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def addFights(fights: List[Fight]): IO[Unit] = {
      for {
        collection <- fightCollection
        _          <- if (fights.nonEmpty) IO.fromFuture(IO(collection.insertMany(fights).toFuture())) else IO.unit
      } yield ()
    }

    override def updateFight(fight: Fight): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.replaceOne(equal(idField, fight.id), fight)
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def updateFightScores(fights: List[Fight]): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = () =>
          collection.bulkWrite(fights.map(f =>
            UpdateOneModel(
              equal(idField, f.id),
              combine(
                Array(Option(set("scores", f.scores)), f.status.map(set("status", _))).filter(_.isDefined).map(_.get)
                  .toIndexedSeq: _*
              )
            )
          ))
        _ <- if (fights.nonEmpty) IO.fromFuture(IO(statement().toFuture())) else IO.unit
      } yield ()
    }

    override def removeFight(competitionId: String)(id: String): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.deleteOne(and(equal(competitionIdField, competitionId), equal(idField, id)))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeFights(competitionId: String)(ids: List[String]): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.deleteMany(and(in(idField, ids), equal(competitionIdField, competitionId)))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeFightsForCategory(competitionId: String)(categoryId: String): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection
          .deleteMany(and(equal("categoryId", categoryId), equal(competitionIdField, competitionId)))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }
    override def updateFightOrderAndMat(updates: List[FightOrderUpdateExtended]): IO[Unit] = {
      for {
        collection <- fightCollection
        writes = () =>
          updates.map(f =>
            UpdateOneModel(
              and(equal(competitionIdField, f.competitionId), equal(idField, f.fightOrderUpdate.fightId)),
              combine(
                set("matId", f.fightOrderUpdate.matId),
                set("numberOnMat", f.fightOrderUpdate.numberOnMat),
                set("matOrder", f.newMat.matOrder),
                set("matName", f.newMat.name),
                set("startTime", Date.from(f.fightOrderUpdate.getStartTime.asJavaInstant))
              )
            )
          )
        _ <- if (updates.nonEmpty) IO.fromFuture(IO(collection.bulkWrite(writes()).toFuture())) else IO.unit
      } yield ()
    }

    override def updateFightStartTime(fights: List[FightStartTimeUpdate]): IO[Unit] = {
      for {
        collection <- fightCollection
        writes = () =>
          fights.map(f =>
            UpdateOneModel(
              equal(idField, f.id),
              combine(
                set("matId", f.matId.orNull),
                set("matName", f.matName.orNull),
                set("matOrder", f.matOrder.getOrElse(-1)),
                set("numberOnMat", f.numberOnMat.getOrElse(-1)),
                set("periodId", f.periodId.orNull),
                set("startTime", f.startTime.orNull),
                set("invalid", f.invalid.getOrElse(false)),
                set("scheduleEntryId", f.scheduleEntryId.orNull)
              )
            )
          )

        _ <- if (fights.nonEmpty) IO.fromFuture(IO(collection.bulkWrite(writes()).toFuture())) else IO.unit
      } yield ()
    }

    override def removeFightsForCompetition(competitionId: String): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.deleteMany(equal(competitionIdField, competitionId))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def updateFightScoresAndResultAndStatus(
      competitionId: String
    )(fightId: String, scores: List[CompScore], fightResult: FightResult, status: FightStatus): IO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.updateOne(
          and(equal(idField, fightId), equal(competitionIdField, competitionId)),
          Seq(set(this.scores, scores), set(this.fightResult, fightResult), set(this.status, status))
        )
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }
  }
}
