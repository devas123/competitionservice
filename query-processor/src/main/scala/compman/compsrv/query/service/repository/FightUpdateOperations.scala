package compman.compsrv.query.service.repository

import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model.{Fight, FightStartTimeUpdate}
import org.mongodb.scala.MongoClient
import org.mongodb.scala.model.UpdateOneModel
import zio.{Ref, RIO, ZIO}
import zio.interop.catz._

trait FightUpdateOperations[F[+_]] {
  def addFight(fight: Fight): F[Unit]
  def addFights(fights: List[Fight]): F[Unit]
  def updateFight(fight: Fight): F[Unit]
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
  }

  def live(mongo: MongoClient, name: String)(implicit
    log: CompetitionLogging.Service[LIO]
  ): FightUpdateOperations[LIO] = new FightUpdateOperations[LIO] with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def idField: String = "id"

    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._

    override def addFight(fight: Fight): LIO[Unit] = {
      val statement = fightCollection.insertOne(fight)
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def addFights(fights: List[Fight]): LIO[Unit] = {
      val statement = fightCollection.insertMany(fights)
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateFight(fight: Fight): LIO[Unit] = {
      val statement = fightCollection.replaceOne(equal(idField, fight.id), fight)
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateFightScores(fights: List[Fight]): LIO[Unit] = {
      val statement = fightCollection.bulkWrite(fights.map(f =>
        UpdateOneModel(equal(idField, f.id), combine(set("scores", f.scores), set("status", f.status)))
      ))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeFight(competitionId: String)(id: String): LIO[Unit] = {
      val statement = fightCollection.deleteOne(and(equal("competitionId", competitionId), equal(idField, id)))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeFights(competitionId: String)(ids: List[String]): LIO[Unit] = {
      val statement = fightCollection.deleteMany(and(in(idField, ids), equal("competitionId", competitionId)))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeFightsForCategory(competitionId: String)(categoryId: String): LIO[Unit] = {
      val statement = fightCollection
        .deleteMany(and(equal("categoryId", categoryId), equal("competitionId", competitionId)))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def updateFightStartTime(fights: List[FightStartTimeUpdate]): LIO[Unit] = {
      val statement = fightCollection.bulkWrite(fights.map(f =>
        UpdateOneModel(
          equal(idField, f.id),
          combine(
            set("matId", f.matId),
            set("matName", f.matName),
            set("matOrder", f.matOrder),
            set("numberOnMat", f.numberOnMat),
            set("periodId", f.periodId),
            set("startTime", f.startTime),
            set("invalid", f.invalid),
            set("scheduleEntryId", f.scheduleEntryId)
          )
        )
      ))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

    override def removeFightsForCompetition(competitionId: String): LIO[Unit] = {
      val statement = fightCollection.deleteMany(equal("competitionId", competitionId))
      RIO.fromFuture(_ => statement.toFuture()).map(_ => ())
    }

  }
}
