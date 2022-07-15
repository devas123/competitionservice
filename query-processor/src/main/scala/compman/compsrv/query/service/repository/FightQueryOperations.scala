package compman.compsrv.query.service.repository

import cats.implicits._
import cats.Monad
import cats.effect.IO
import compman.compsrv.query.model._
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.BsonNull
import org.mongodb.scala.model.Filters.{and, equal, not, size}
import org.mongodb.scala.model.Sorts

import java.util.concurrent.atomic.AtomicReference

trait FightQueryOperations[F[+_]] {
  def getFightsByScheduleEntries(competitionId: String): F[List[FightByScheduleEntry]]
  def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): F[Int]
  def getNumberOfFightsForMat(competitionId: String)(matId: String): F[Int]
  def getFightsByMat(competitionId: String)(matId: String, limit: Int): F[List[Fight]]
  def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): F[List[Fight]]

  def getFightById(competitionId: String)(id: String): F[Option[Fight]]
  def getFightIdsByCategoryIds(competitionId: String): F[Map[String, List[String]]]
  def getFightsByIds(competitionId: String)(ids: Set[String]): F[List[Fight]]
}

object FightQueryOperations {
  def apply[F[+_]](implicit F: FightQueryOperations[F]): FightQueryOperations[F] = F

  def test[F[+_]: Monad](
    fights: Option[AtomicReference[Map[String, Fight]]] = None,
    stages: Option[AtomicReference[Map[String, StageDescriptor]]] = None
  ): FightQueryOperations[F] = new FightQueryOperations[F] with CommonTestOperations {
    override def getFightsByMat(competitionId: String)(matId: String, limit: Int): F[List[Fight]] = fights match {
      case Some(value) => Monad[F]
          .pure(value.get.values.filter(f => f.competitionId == competitionId && f.matId.contains(matId)).toList)
      case None => Monad[F].pure(List.empty)
    }
    override def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): F[List[Fight]] =
      fights match {
        case Some(value) => Monad[F]
            .pure(value.get.values.filter(f => f.competitionId == competitionId && f.stageId == stageId).toList)
        case None => Monad[F].pure(List.empty)
      }
    override def getFightById(competitionId: String)(id: String): F[Option[Fight]] = getById[F, Fight](fights)(id)
    override def getFightsByIds(competitionId: String)(ids: Set[String]): F[List[Fight]] = ids.toList
      .traverse(getById[F, Fight](fights)).map(_.mapFilter(identity))

    override def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): F[Int] = for {
      stages      <- getStagesByCategory[F](stages)(competitionId)(categoryId)
      stageFights <- stages.traverse(s => getFightsByStage(competitionId)(categoryId, s.id))
    } yield stageFights.flatten.size

    override def getFightIdsByCategoryIds(competitionId: String): F[Map[String, List[String]]] = fights match {
      case Some(value) => Monad[F].pure(
          value.get.values.filter(f => f.competitionId == competitionId).map(f => (f.categoryId, f.id))
            .groupMap(_._1)(_._2).view.mapValues(_.toList).toMap
        )
      case None => Monad[F].pure(Map.empty)
    }

    override def getFightsByScheduleEntries(competitionId: String): F[List[FightByScheduleEntry]] = fights
      .map(_.get.filter(_._2.competitionId == competitionId).map(e => {
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
      }).toList).map(Monad[F].pure).getOrElse(Monad[F].pure(List.empty))

    override def getNumberOfFightsForMat(competitionId: String)(matId: String): F[Int] = for {
      compStages <- stages match {
        case Some(value) => Monad[F].pure(value.get.values.toList)
        case None        => Monad[F].pure(List.empty[StageDescriptor])
      }
      stageFights <- compStages.traverse(s => getFightsByStage(competitionId)(s.categoryId, s.id))
    } yield stageFights.flatten.size

  }

  def live(mongo: MongoClient, name: String): FightQueryOperations[IO] = new FightQueryOperations[IO]
    with CommonLiveOperations with FightFieldsAndFilters {

    private def activeFights(competitionId: String) = and(
      equal(competitionIdField, competitionId),
      activeFight,
      not(equal("scores", BsonNull())),
      not(equal("scores.competitorId", BsonNull())),
      not(size("scores", 0))
    )

    private def completableFights(competitionId: String) =
      and(equal(competitionIdField, competitionId), completableFightsStatuses)

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    import org.mongodb.scala.model.Filters._

    override def getFightsByMat(competitionId: String)(matId: String, limit: Int): IO[List[Fight]] = {
      for {
        collection <- fightCollection
        statement = collection.find(and(activeFights(competitionId), equal("matId", matId))).limit(limit)
        res <- IO.fromFuture(IO(statement.toFuture())).map(_.toList)
      } yield res
    }

    override def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): IO[List[Fight]] = {
      for {
        collection <- fightCollection
        statement = collection.find(
          and(equal(competitionIdField, competitionId), equal("stageId", stageId), equal(categoryIdField, categoryId))
        ).sort(Sorts.ascending("bracketsInfo.round", "bracketsInfo.numberInRound"))
        res <- IO.fromFuture(IO(statement.toFuture())).map(_.toList)
      } yield res

    }

    override def getFightById(competitionId: String)(id: String): IO[Option[Fight]] = {
      for {
        collection <- fightCollection
        res        <- getByIdAndCompetitionId(collection)(competitionId, id)
      } yield res
    }

    override def getFightsByIds(competitionId: String)(ids: Set[String]): IO[List[Fight]] = {
      import cats.implicits._
      ids.toList.traverse(id => getFightById(competitionId)(id).map(_.get))
    }
    override def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): IO[Int] = {
      for {
        collection <- fightCollection
        statement = collection.countDocuments(
          and(equal(competitionIdField, competitionId), equal(categoryIdField, categoryId), activeFight)
        )
        res <- IO.fromFuture(IO(statement.toFuture())).map(_.toInt)
      } yield res
    }

    override def getNumberOfFightsForMat(competitionId: String)(matId: String): IO[Int] = {
      for {
        collection <- fightCollection
        statement = collection.countDocuments(and(equal(competitionIdField, competitionId), equal("matId", matId)))
        res <- IO.fromFuture(IO(statement.toFuture())).map(_.toInt)
      } yield res
    }

    override def getFightIdsByCategoryIds(competitionId: String): IO[Map[String, List[String]]] = {
      for {
        collection <- fightCollection
        statement = collection.find(completableFights(competitionId))
        res <- IO.fromFuture(IO(statement.toFuture())).map(_.groupMap(_.categoryId)(_.id).map(e => e._1 -> e._2.toList))
      } yield res
    }

    override def getFightsByScheduleEntries(competitionId: String): IO[List[FightByScheduleEntry]] = {
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
        res <- IO.fromFuture(IO(statement.toFuture())).map(
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
