package compman.compsrv.query.service.repository

import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.BsonNull
import org.mongodb.scala.model.Filters.{and, equal, not, size}
import org.mongodb.scala.model.Sorts
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

  def live(mongo: MongoClient, name: String): FightQueryOperations[LIO] = new FightQueryOperations[LIO]
    with CommonLiveOperations with FightFieldsAndFilters {

    private def activeFights(competitionId: String) = and(
      equal(competitionIdField, competitionId),
      statusCheck,
      not(equal("scores", BsonNull())),
      not(equal("scores.competitorId", BsonNull())),
      not(size("scores", 0))
    )

    private def completableFights(competitionId: String) =
      and(equal(competitionIdField, competitionId), completableFightsStatuses)

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    import org.mongodb.scala.model.Filters._

    override def getFightsByMat(competitionId: String)(matId: String, limit: Int): LIO[List[Fight]] = {
      for {
        collection <- fightCollection
        statement = collection.find(and(activeFights(competitionId), equal("matId", matId))).limit(limit)
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_.toList)
      } yield res
    }

    override def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): LIO[List[Fight]] = {
      for {
        collection <- fightCollection
        statement = collection.find(
          and(equal(competitionIdField, competitionId), equal("stageId", stageId), equal(categoryIdField, categoryId))
        ).sort(Sorts.ascending("bracketsInfo.round", "bracketsInfo.numberInRound"))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_.toList)
      } yield res

    }

    override def getFightById(competitionId: String)(categoryId: String, id: String): LIO[Option[Fight]] = {
      for {
        collection <- fightCollection
        statement = collection
          .find(and(equal(competitionIdField, competitionId), equal(idField, id), equal(categoryIdField, categoryId)))
        res <- RIO.fromFuture(_ => statement.headOption())
      } yield res
    }

    override def getFightsByIds(competitionId: String)(categoryId: String, ids: Set[String]): LIO[List[Fight]] = {
      import cats.implicits._
      import zio.interop.catz._
      ids.toList.traverse(id => getFightById(competitionId)(categoryId, id).map(_.get))
    }
    override def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): LIO[Int] = {
      for {
        collection <- fightCollection
        statement = collection
          .countDocuments(and(equal(competitionIdField, competitionId), equal(categoryIdField, categoryId)))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_.toInt)
      } yield res
    }

    override def getNumberOfFightsForMat(competitionId: String)(matId: String): LIO[Int] = {
      for {
        collection <- fightCollection
        statement = collection.countDocuments(and(equal(competitionIdField, competitionId), equal("matId", matId)))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(_.toInt)
      } yield res
    }

    override def getFightIdsByCategoryIds(competitionId: String): LIO[Map[String, List[String]]] = {
      for {
        collection <- fightCollection
        statement = collection.find(completableFights(competitionId))
        res <- RIO.fromFuture(_ => statement.toFuture())
          .map(_.groupMap(_.categoryId)(_.id).map(e =>
            e._1 -> e._2.toList
          ))
      } yield res
    }

    override def getFightsByScheduleEntries(competitionId: String): LIO[List[FightByScheduleEntry]] = {
      for {
        collection <- fightCollection
        periodId        = "periodId"
        scheduleEntryId = "scheduleEntryId"
        statement = collection.find(and(
          equal(competitionIdField, competitionId),
          not(equal(periodId, null)),
          exists(periodId),
          exists(scheduleEntryId),
          not(equal(scheduleEntryId, null))
        ))
        res <- RIO.fromFuture(_ => statement.toFuture()).map(
          _.filter(_.scheduleEntryId.isDefined).map(f =>
            FightByScheduleEntry(
              scheduleEntryId = f.scheduleEntryId.get,
              categoryId = f.categoryId,
              periodId = f.periodId.get,
              competitionId = f.competitionId,
              matId = f.matId,
              fightId = f.id,
              startTime = f.startTime
            )
          ).toList
        )
      } yield res

    }
  }
}
