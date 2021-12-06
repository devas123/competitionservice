package compman.compsrv.query.service.repository

import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.query.model.{CompScore, Fight, FightResult, FightStartTimeUpdate}
import org.mongodb.scala.MongoClient
import org.mongodb.scala.model.UpdateOneModel
import zio.{RIO, Ref, ZIO}
import zio.interop.catz._

trait FightUpdateOperations[F[+_]] {
  def addFight(fight: Fight): F[Unit]
  def addFights(fights: List[Fight]): F[Unit]
  def updateFight(fight: Fight): F[Unit]
  def updateFightScoresAndResultAndStatus(
    competitionId: String
  )(fightId: String, scores: List[CompScore], fightResult: FightResult, status: FightStatus): F[Unit]
  def updateFightScores(fights: List[Fight]): F[Unit]
  def updateFightStartTime(fights: List[FightStartTimeUpdate]): F[Unit]
  def removeFight(competitionId: String)(id: String): F[Unit]
  def removeFights(competitionId: String)(ids: List[String]): F[Unit]
  def removeFightsForCategory(competitionId: String)(categoryId: String): F[Unit]
  def removeFightsForCompetition(competitionId: String): F[Unit]
}

object FightUpdateOperations {
  def apply[F[+_]](implicit F: FightUpdateOperations[F]): FightUpdateOperations[F] = F

  def test(fights: Option[Ref[Map[String, Fight]]] = None): FightUpdateOperations[LIO] = new FightUpdateOperations[LIO]
    with CommonTestOperations {
    override def removeFightsForCategory(competitionId: String)(categoryId: String): LIO[Unit] = fights
      .map(_.update(fs => fs.filter(f => f._2.categoryId != categoryId))).getOrElse(ZIO.unit)

    override def updateFightStartTime(fights: List[FightStartTimeUpdate]): LIO[Unit] = updateFightScores(List.empty)
    override def addFight(fight: Fight): LIO[Unit] = add(fights)(fight.id)(Some(fight))

    override def addFights(fights: List[Fight]): LIO[Unit] = fights.traverse(addFight).map(_ => ())

    override def updateFight(fight: Fight): LIO[Unit] = update(fights)(fight.id)(_ => fight)

    override def updateFightScores(fights: List[Fight]): LIO[Unit] = fights.traverse(updateFight).map(_ => ())

    override def removeFight(competitionId: String)(id: String): LIO[Unit] = remove(fights)(id)

    override def removeFights(competitionId: String)(ids: List[String]): LIO[Unit] = ids
      .traverse(removeFight(competitionId)).map(_ => ())

    override def removeFightsForCompetition(competitionId: String): LIO[Unit] = fights
      .map(_.update(_.filter(_._2.competitionId != competitionId))).getOrElse(ZIO.unit)

    override def updateFightScoresAndResultAndStatus(
      competitionId: String
    )(fightId: String, scores: List[CompScore], fightResult: FightResult, status: FightStatus): LIO[Unit] =
      update(fights)(fightId)(f => f.copy(scores = scores, fightResult = Option(fightResult), status = Option(status)))
  }

  def live(mongo: MongoClient, name: String): FightUpdateOperations[LIO] = new FightUpdateOperations[LIO]
    with CommonLiveOperations with FightFieldsAndFilters {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def idField: String = "id"

    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._

    override def addFight(fight: Fight): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.insertOne(fight)
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def addFights(fights: List[Fight]): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.insertMany(fights)
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def updateFight(fight: Fight): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.replaceOne(equal(idField, fight.id), fight)
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def updateFightScores(fights: List[Fight]): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.bulkWrite(fights.map(f =>
          UpdateOneModel(
            equal(idField, f.id),
            combine(
              Array(Option(set("scores", f.scores)), f.status.map(set("status", _))).filter(_.isDefined).map(_.get): _*
            )
          )
        ))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def removeFight(competitionId: String)(id: String): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.deleteOne(and(equal(competitionIdField, competitionId), equal(idField, id)))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def removeFights(competitionId: String)(ids: List[String]): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.deleteMany(and(in(idField, ids), equal(competitionIdField, competitionId)))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def removeFightsForCategory(competitionId: String)(categoryId: String): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection
          .deleteMany(and(equal("categoryId", categoryId), equal(competitionIdField, competitionId)))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def updateFightStartTime(fights: List[FightStartTimeUpdate]): LIO[Unit] = {
      for {
        collection <- fightCollection
        writes = fights.map(f =>
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

        res <-
          if (writes.nonEmpty) { RIO.fromFuture(_ => collection.bulkWrite(writes).toFuture()).map(_ => ()) }
          else { ZIO.unit }
      } yield res
    }

    override def removeFightsForCompetition(competitionId: String): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.deleteMany(equal(competitionIdField, competitionId))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }

    override def updateFightScoresAndResultAndStatus(
      competitionId: String
    )(fightId: String, scores: List[CompScore], fightResult: FightResult, status: FightStatus): LIO[Unit] = {
      for {
        collection <- fightCollection
        statement = collection.updateOne(
          and(equal(idField, fightId), equal(competitionIdField, competitionId)),
          Seq(set(this.scores, scores), set(this.fightResult, fightResult), set(this.status, status))
        )
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
      } yield res
    }
  }
}
