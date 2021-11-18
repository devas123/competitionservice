package compman.compsrv.query.service.repository

import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import org.mongodb.scala.MongoClient
import zio.{Ref, RIO, Task}
import zio.interop.catz._

trait FightQueryOperations[F[+_]] {
  def getFightsByScheduleEntries(competitionId: String): F[List[FightByScheduleEntry]]
  def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): F[Int]
  def getNumberOfFightsForMat(competitionId: String)(matId: String): F[Int]
  def getFightsByMat(competitionId: String)(matId: String, limit: Int): F[List[Fight]]
  def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): F[List[Fight]]

  def getFightById(competitionId: String)(categoryId: String, id: String): F[Option[Fight]]
  def getFightIdsByCategoryIds(competitionId: String): F[Map[String, List[String]]]
  def getFightsByIds(competitionId: String)(categoryId: String, ids: Set[String]): F[List[Fight]]
}

object FightQueryOperations {
  def apply[F[+_]](implicit F: FightQueryOperations[F]): FightQueryOperations[F] = F

  def test(
    fights: Option[Ref[Map[String, Fight]]] = None,
    stages: Option[Ref[Map[String, StageDescriptor]]] = None
  ): FightQueryOperations[LIO] = new FightQueryOperations[LIO] with CommonTestOperations {
    override def getFightsByMat(competitionId: String)(matId: String, limit: Int): LIO[List[Fight]] = fights match {
      case Some(value) => value.get
          .map(_.values.filter(f => f.competitionId == competitionId && f.matId.contains(matId)).toList)
      case None => Task(List.empty)
    }
    override def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): LIO[List[Fight]] =
      fights match {
        case Some(value) => value.get
            .map(_.values.filter(f => f.competitionId == competitionId && f.stageId == stageId).toList)
        case None => Task(List.empty)
      }
    override def getFightById(competitionId: String)(categoryId: String, id: String): LIO[Option[Fight]] =
      getById(fights)(id)
    override def getFightsByIds(competitionId: String)(categoryId: String, ids: Set[String]): LIO[List[Fight]] = ids
      .toList.traverse(getById(fights)).map(_.mapFilter(identity))

    override def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): LIO[Int] = for {
      stages      <- getStagesByCategory(stages)(competitionId)(categoryId)
      stageFights <- stages.traverse(s => getFightsByStage(competitionId)(categoryId, s.id))
    } yield stageFights.flatten.size

    override def getFightIdsByCategoryIds(competitionId: String): LIO[Map[String, List[String]]] = fights match {
      case Some(value) => value.get.map(
          _.values.filter(f => f.competitionId == competitionId).map(f => (f.categoryId, f.id)).groupMap(_._1)(_._2)
            .view.mapValues(_.toList).toMap
        )
      case None => Task(Map.empty)
    }

    override def getFightsByScheduleEntries(competitionId: String): LIO[List[FightByScheduleEntry]] = fights
      .map(_.get.map(_.filter(_._2.competitionId == competitionId).map(e => {
        val scheduleEntry = e._2.scheduleEntryId.getOrElse("")
        FightByScheduleEntry(
          categoryId = e._2.categoryId,
          scheduleEntryId = scheduleEntry,
          competitionId = competitionId,
          matId = e._2.matId,
          fightId = e._2.id,
          startTime = e._2.startTime,
          periodId = e._2.periodId.getOrElse("")
        )
      }).toList)).getOrElse(Task(List.empty))

    override def getNumberOfFightsForMat(competitionId: String)(matId: String): LIO[Int] = for {
      compStages <- stages match {
        case Some(value) => value.get.map(_.values.toList)
        case None        => Task(List.empty)
      }
      stageFights <- compStages.traverse(s => getFightsByStage(competitionId)(s.categoryId, s.id))
    } yield stageFights.flatten.size

  }

  def live(
            mongo: MongoClient, name: String
  )(implicit log: CompetitionLogging.Service[LIO]): FightQueryOperations[LIO] = new FightQueryOperations[LIO] with CommonLiveOperations {


    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def idField: String = "id"

    import org.mongodb.scala.model.Filters._

    override def getFightsByMat(competitionId: String)(matId: String, limit: Int): LIO[List[Fight]] = {
      val statement = fightCollection
        .find(and(equal("competitionId", competitionId), equal("matId", matId)))
        .limit(limit)
      RIO.fromFuture(_ => statement.toFuture()).map(_.toList)
    }

    override def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): LIO[List[Fight]] = {
      val statement = fightCollection
        .find(and(equal("competitionId", competitionId), equal("stageId", stageId), equal("categoryId", categoryId)))
      RIO.fromFuture(_ => statement.toFuture()).map(_.toList)
    }

    override def getFightById(competitionId: String)(categoryId: String, id: String): LIO[Option[Fight]] = {
      val statement = fightCollection
        .find(and(equal("competitionId", competitionId), equal(idField, id), equal("categoryId", categoryId)))
      RIO.fromFuture(_ => statement.headOption())
    }

    override def getFightsByIds(competitionId: String)(categoryId: String, ids: Set[String]): LIO[List[Fight]] = {
      val statement = fightCollection
        .find(in(idField, ids))
      RIO.fromFuture(_ => statement.toFuture()).map(_.toList)
    }

    override def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): LIO[Int] = {
      val statement = fightCollection
        .countDocuments(and(equal("competitionId", competitionId), equal("categoryId", categoryId)))
      RIO.fromFuture(_ => statement.toFuture()).map(_.toInt)
    }

    override def getNumberOfFightsForMat(competitionId: String)(matId: String): LIO[Int] = {
      val statement = fightCollection
        .countDocuments(and(equal("competitionId", competitionId), equal("matId", matId)))
      RIO.fromFuture(_ => statement.toFuture()).map(_.toInt)
    }

    override def getFightIdsByCategoryIds(competitionId: String): LIO[Map[String, List[String]]] = {
      val statement = fightCollection
        .find(equal("competitionId", competitionId))
      RIO.fromFuture(_ => statement.toFuture()).map(_.groupMap(_.categoryId)(_.id).map(e => e._1 -> e._2.toList))
    }

    override def getFightsByScheduleEntries(competitionId: String): LIO[List[FightByScheduleEntry]] = {
      val statement = fightCollection
        .find(and(equal("competitionId", competitionId), exists("scheduleEntryId"), exists("periodId")))
      RIO.fromFuture(_ => statement.toFuture()).map(_.map(f => FightByScheduleEntry(
        scheduleEntryId = f.scheduleEntryId.get,
        categoryId = f.categoryId,
        periodId = f.periodId.get,
        competitionId = f.competitionId,
        matId = f.matId,
        fightId = f.id,
        startTime = f.startTime,
      )).toList)
    }
  }
}