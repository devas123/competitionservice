package compman.compsrv.query.service.repository

import cats.Monad
import cats.effect.IO
import cats.implicits._
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionState.CompetitionInfoTemplate
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._

import java.util.concurrent.atomic.AtomicReference

trait CompetitionQueryOperations[F[+_]] {
  def getCompetitionProperties(id: String): F[Option[CompetitionProperties]]
  def getCategoriesByCompetitionId(competitionId: String): F[List[Category]]
  def getNumberOfCompetitorsForCategory(competitionId: String)(categoryId: String): F[Int]

  def getCompetitionInfoTemplate(competitionId: String): F[Option[CompetitionInfoTemplate]]

  def getCategoryById(competitionId: String)(id: String): F[Option[Category]]

  def searchCategory(
    competitionId: String
  )(searchString: String, pagination: Option[Pagination]): F[(List[Category], Pagination)]

  def getCompetitorById(competitionId: String)(id: String): F[Option[Competitor]]

  def getCompetitorsByCategoryId(competitionId: String)(
    categoryId: String,
    pagination: Option[Pagination],
    searchString: Option[String] = None
  ): F[(List[Competitor], Pagination)]

  def getCompetitorsByCompetitionId(
    competitionId: String
  )(pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)]

  def getCompetitorsByAcademyId(competitionId: String)(
    academyId: String,
    pagination: Option[Pagination],
    searchString: Option[String] = None
  ): F[(List[Competitor], Pagination)]

  def getRegistrationInfo(competitionId: String): F[Option[RegistrationInfo]]

  def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): F[List[ScheduleEntry]]

  def getScheduleRequirementsByPeriodId(competitionId: String)(periodId: String): F[List[ScheduleRequirement]]

  def getPeriodsByCompetitionId(competitionId: String): F[List[Period]]

  def getPeriodById(competitionId: String)(id: String): F[Option[Period]]

  def getStagesByCategory(competitionId: String)(categoryId: String): F[List[StageDescriptor]]
  def getStageById(competitionId: String)(id: String): F[Option[StageDescriptor]]
}

object CompetitionQueryOperations {
  def apply[F[+_]](implicit F: CompetitionQueryOperations[F]): CompetitionQueryOperations[F] = F

  def test[F[+_]: Monad](
    competitionProperties: Option[AtomicReference[Map[String, CompetitionProperties]]] = None,
    registrationInfo: Option[AtomicReference[Map[String, RegistrationInfo]]] = None,
    categories: Option[AtomicReference[Map[String, Category]]] = None,
    competitors: Option[AtomicReference[Map[String, Competitor]]] = None,
    periods: Option[AtomicReference[Map[String, Period]]] = None,
    stages: Option[AtomicReference[Map[String, StageDescriptor]]] = None
  ): CompetitionQueryOperations[F] = new CompetitionQueryOperations[F] with CommonTestOperations {

    override def getCompetitionProperties(id: String): F[Option[CompetitionProperties]] =
      getById[F, CompetitionProperties](competitionProperties)(id)

    override def getCategoriesByCompetitionId(competitionId: String): F[List[Category]] = {
      categories match {
        case Some(value) => Monad[F].pure(value.get.values.filter(_.competitionId == competitionId).toList)
        case None        => Monad[F].pure(List.empty)
      }
    }

    override def getCompetitionInfoTemplate(competitionId: String): F[Option[CompetitionInfoTemplate]] = Monad[F]
      .pure(none)

    override def getCategoryById(competitionId: String)(id: String): F[Option[Category]] =
      getById[F, Category](categories)(id)

    override def searchCategory(
      competitionId: String
    )(searchString: String, pagination: Option[Pagination]): F[(List[Category], Pagination)] =
      getCategoriesByCompetitionId(competitionId).map(cats => (cats, Pagination(0, cats.size, cats.size)))

    override def getCompetitorById(competitionId: String)(id: String): F[Option[Competitor]] =
      getById[F, Competitor](competitors)(id)

    override def getCompetitorsByCategoryId(competitionId: String)(
      categoryId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): F[(List[Competitor], Pagination)] = competitors match {
      case Some(value) =>
        val list = value.get.values.toList.filter(_.categories.contains(categoryId))
        Monad[F].pure((list, Pagination(0, list.size, list.size)))
      case None => Monad[F].pure((List.empty, Pagination(0, 0, 0)))
    }

    override def getCompetitorsByCompetitionId(
      competitionId: String
    )(pagination: Option[Pagination], searchString: Option[String]): F[(List[Competitor], Pagination)] = {
      competitors match {
        case Some(value) =>
          val list = value.get.values.toList.filter(_.competitionId.equals(competitionId))
          Monad[F].pure((list, Pagination(0, list.size, list.size)))
        case None => Monad[F].pure((List.empty, Pagination(0, 0, 0)))
      }
    }

    override def getCompetitorsByAcademyId(competitionId: String)(
      academyId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): F[(List[Competitor], Pagination)] = competitors match {
      case Some(value) =>
        val list = value.get.values.toList.filter(_.academy.exists(_.academyId == academyId))
        Monad[F].pure((list, Pagination(0, list.size, list.size)))
      case None => Monad[F].pure((List.empty, Pagination(0, 0, 0)))
    }

    override def getRegistrationInfo(competitionId: String): F[Option[RegistrationInfo]] =
      getById[F, RegistrationInfo](registrationInfo)(competitionId)

    override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): F[List[ScheduleEntry]] =
      getPeriodById(competitionId)(periodId).map(_.map(_.scheduleEntries).getOrElse(List.empty))

    override def getScheduleRequirementsByPeriodId(
      competitionId: String
    )(periodId: String): F[List[ScheduleRequirement]] = getPeriodById(competitionId)(periodId)
      .map(_.map(_.scheduleRequirements).getOrElse(List.empty))

    override def getPeriodsByCompetitionId(competitionId: String): F[List[Period]] = periods match {
      case Some(value) => Monad[F].pure(value.get.values.toList.filter(_.competitionId.eq(competitionId)))
      case None        => Monad[F].pure(List.empty)
    }

    override def getPeriodById(competitionId: String)(id: String): F[Option[Period]] = getById[F, Period](periods)(id)

    override def getStagesByCategory(competitionId: String)(categoryId: String): F[List[StageDescriptor]] =
      getStagesByCategory[F](stages)(competitionId)(categoryId)

    override def getStageById(competitionId: String)(id: String): F[Option[StageDescriptor]] =
      getById[F, StageDescriptor](stages)(id)

    override def getNumberOfCompetitorsForCategory(competitionId: String)(categoryId: String): F[Int] = (for {
      cmtrs <- competitors
      result = cmtrs.get.values.count(_.categories.contains(categoryId))
    } yield Monad[F].pure(result)).getOrElse(Monad[F].pure(0))
  }

  def live(mongo: MongoClient, name: String): CompetitionQueryOperations[IO] = new CompetitionQueryOperations[IO]
    with CommonLiveOperations {

    import org.mongodb.scala.model.Projections._

    private val includeCompetitionInfoProjection = include("infoTemplate")
    private val includeRegistrationInfoProjection = include("registrationInfo")
    private val includePeriodsProjection = include("periods")

    private val competitionPropertiesProjection = include("properties")
    private val categoriesProjection            = include("categories")

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def getCompetitionProperties(id: String): IO[Option[CompetitionProperties]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(equal(idField, id)).projection(competitionPropertiesProjection).map(_.properties)
        res <- selectOne(select)
      } yield res.flatten
    }

    override def getCategoriesByCompetitionId(competitionId: String): IO[List[Category]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(equal(idField, competitionId)).projection(categoriesProjection).map(_.categories)
        res <- IO.fromFuture(IO(select.toFuture()))
      } yield res.headOption.flatten.map(_.values.toList).getOrElse(List.empty)
    }

    override def getCompetitionInfoTemplate(competitionId: String): IO[Option[CompetitionInfoTemplate]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(equal(idField, competitionId)).projection(includeCompetitionInfoProjection)
          .map(_.infoTemplate)
        res <- selectOne(select)
      } yield res.flatten
    }

    override def getCategoryById(competitionId: String)(id: String): IO[Option[Category]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId), exists(s"categories.$id")))
          .projection(categoriesProjection).map(_.categories.get(id))
        res <- IO.fromFuture(IO(select.headOption()))
      } yield res
    }

    override def searchCategory(
      competitionId: String
    )(searchString: String, pagination: Option[Pagination]): IO[(List[Category], Pagination)] = {
      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(30)
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId))).projection(categoriesProjection)
        res <- IO.fromFuture(IO(select.head())).map(_.categories)
      } yield (
        res.map(_.filter(c =>
          searchString == null || searchString.isBlank ||
            c._2.restrictions.exists(_.name.exists(_.matches(searchString)))
        ).slice(drop, drop + take).values.toList).getOrElse(List.empty),
        pagination.getOrElse(Pagination(0, res.map(_.size).getOrElse(0), res.map(_.size).getOrElse(0)))
      )
    }

    override def getCompetitorById(competitionId: String)(id: String): IO[Option[Competitor]] = {
      for {
        collection <- competitorCollection
        select = collection.find(and(equal(competitionIdField, competitionId), equal(idField, id)))
        res <- IO.fromFuture(IO(select.headOption()))
      } yield res
    }

    override def getCompetitorsByCategoryId(competitionId: String)(
      categoryId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): IO[(List[Competitor], Pagination)] = {
      val drop   = pagination.map(_.offset).getOrElse(0)
      val take   = pagination.map(_.maxResults).getOrElse(0)
      val filter = and(equal(competitionIdField, competitionId), equal("categories", categoryId))
      for {
        collection <- competitorCollection
        select = collection.find(filter).skip(drop).limit(take)
        total  = IO.fromFuture(IO(collection.countDocuments(filter).toFuture()))
        res <- selectWithPagination(select, pagination, total)
      } yield res
    }

    override def getCompetitorsByCompetitionId(
      competitionId: String
    )(pagination: Option[Pagination], searchString: Option[String]): IO[(List[Competitor], Pagination)] = {

      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(0)
      for {
        collection <- competitorCollection
        select = collection.find(equal(competitionIdField, competitionId)).skip(drop).limit(take)
        total  = IO.fromFuture(IO(collection.countDocuments(equal(competitionIdField, competitionId)).toFuture()))
        res <- selectWithPagination(select, pagination, total)
      } yield res
    }

    override def getCompetitorsByAcademyId(competitionId: String)(
      academyId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): IO[(List[Competitor], Pagination)] = {
      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(0)
      for {
        collection <- competitorCollection
        select = collection.find(and(equal(competitionIdField, competitionId), equal("academy.id", academyId)))
          .skip(drop).limit(take)
        total = IO.fromFuture(IO(collection.countDocuments(equal(competitionIdField, competitionId)).toFuture()))
        res <- selectWithPagination(select, pagination, total)
      } yield res
    }

    private def getStateById(competitionId: String)(projection: Bson*) = {
      for {
        collection <- competitionStateCollection
        select =
          if (projection.nonEmpty) collection.find(equal(idField, competitionId))
            .projection(projection.reduce((a, b) => and(a, b)))
          else collection.find(equal(idField, competitionId))
        res <- IO.fromFuture(IO(select.headOption()))
      } yield res
    }

    override def getRegistrationInfo(competitionId: String): IO[Option[RegistrationInfo]] = {
      for { res <- getStateById(competitionId)(includeRegistrationInfoProjection).map(_.map(_.registrationInfo)) } yield res.flatten
    }

    override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): IO[List[ScheduleEntry]] = {
      for { res <- getStateById(competitionId)(includePeriodsProjection) } yield res match {
        case Some(value) => value.periods.flatMap(_.get(periodId).map(_.scheduleEntries)).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getScheduleRequirementsByPeriodId(
      competitionId: String
    )(periodId: String): IO[List[ScheduleRequirement]] = {
      for { res <- getStateById(competitionId)(includePeriodsProjection) } yield res match {
        case Some(value) => value.periods.flatMap(_.get(periodId)).map(_.scheduleRequirements).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getPeriodsByCompetitionId(competitionId: String): IO[List[Period]] = {
      for { res <- getStateById(competitionId)(includePeriodsProjection) } yield res match {
        case Some(value) => value.periods.map(_.values.toList).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getPeriodById(competitionId: String)(id: String): IO[Option[Period]] = {
      for { res <- getStateById(competitionId)(include(s"periods.$id")) } yield res match {
        case Some(value) => value.periods.flatMap(_.get(id))
        case None        => None
      }
    }

    override def getStagesByCategory(competitionId: String)(categoryId: String): IO[List[StageDescriptor]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId)))
        res <- IO.fromFuture(IO(select.headOption()))
      } yield res match {
        case Some(value) => value.stages.map(_.values.filter(_.categoryId == categoryId).toList).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getStageById(competitionId: String)(id: String): IO[Option[StageDescriptor]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId), exists(s"stages.$id")))
        res <- IO.fromFuture(IO(select.headOption()))
      } yield res match {
        case Some(value) => value.stages.flatMap(_.get(id))
        case None        => None
      }
    }

    override def getNumberOfCompetitorsForCategory(competitionId: String)(categoryId: String): IO[Int] = {
      for {
        collection <- competitorCollection
        select = collection
          .countDocuments(and(equal(competitionIdField, competitionId), equal("categories", categoryId)))
        res <- IO.fromFuture(IO(select.toFuture())).map(_.toInt)
      } yield res
    }
  }

  def getCompetitionProperties[F[+_]: CompetitionQueryOperations](id: String): F[Option[CompetitionProperties]] =
    CompetitionQueryOperations[F].getCompetitionProperties(id)

  def getCategoriesByCompetitionId[F[+_]: CompetitionQueryOperations](competitionId: String): F[List[Category]] =
    CompetitionQueryOperations[F].getCategoriesByCompetitionId(competitionId)

  def getCompetitionInfoTemplate[F[+_]: CompetitionQueryOperations](
    competitionId: String
  ): F[Option[CompetitionInfoTemplate]] = CompetitionQueryOperations[F].getCompetitionInfoTemplate(competitionId)

  def getCategoryById[F[+_]: CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Category]] =
    CompetitionQueryOperations[F].getCategoryById(competitionId)(id)

  def searchCategory[F[+_]: CompetitionQueryOperations](
    competitionId: String
  )(searchString: String, pagination: Option[Pagination]): F[(List[Category], Pagination)] =
    CompetitionQueryOperations[F].searchCategory(competitionId)(searchString, pagination)

  def getCompetitorById[F[+_]: CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Competitor]] =
    CompetitionQueryOperations[F].getCompetitorById(competitionId)(id)

  def getCompetitorsByCategoryId[F[+_]: CompetitionQueryOperations](competitionId: String)(
    categoryId: String,
    pagination: Option[Pagination],
    searchString: Option[String] = None
  ): F[(List[Competitor], Pagination)] = CompetitionQueryOperations[F]
    .getCompetitorsByCategoryId(competitionId)(categoryId, pagination, searchString)

  def getCompetitorsByCompetitionId[F[+_]: CompetitionQueryOperations](
    competitionId: String
  )(pagination: Option[Pagination], searchString: Option[String] = None): F[(List[Competitor], Pagination)] =
    CompetitionQueryOperations[F].getCompetitorsByCompetitionId(competitionId)(pagination, searchString)

  def getCompetitorsByAcademyId[F[+_]: CompetitionQueryOperations](competitionId: String)(
    academyId: String,
    pagination: Option[Pagination],
    searchString: Option[String] = None
  ): F[(List[Competitor], Pagination)] = CompetitionQueryOperations[F]
    .getCompetitorsByAcademyId(competitionId)(academyId, pagination, searchString)

  def getScheduleEntriesByPeriodId[F[+_]: CompetitionQueryOperations](competitionId: String)(
    periodId: String
  ): F[List[ScheduleEntry]] = CompetitionQueryOperations[F].getScheduleEntriesByPeriodId(competitionId)(periodId)

  def getScheduleRequirementsByPeriodId[F[+_]: CompetitionQueryOperations](
    competitionId: String
  )(periodId: String): F[List[ScheduleRequirement]] = CompetitionQueryOperations[F]
    .getScheduleRequirementsByPeriodId(competitionId)(periodId)

  def getPeriodsByCompetitionId[F[+_]: CompetitionQueryOperations](competitionId: String): F[List[Period]] =
    CompetitionQueryOperations[F].getPeriodsByCompetitionId(competitionId)

  def getPeriodById[F[+_]: CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Period]] =
    CompetitionQueryOperations[F].getPeriodById(competitionId)(id)

}
