package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import org.mongodb.scala.{FindObservable, MongoClient, Observable}
import org.mongodb.scala.model.Filters._
import zio.{Ref, RIO, Task, ZIO}

import scala.concurrent.Future

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

  def getRegistrationGroups(competitionId: String): F[List[RegistrationGroup]]

  def getRegistrationGroupById(competitionId: String)(id: String): F[Option[RegistrationGroup]]

  def getRegistrationPeriods(competitionId: String): F[List[RegistrationPeriod]]

  def getRegistrationPeriodById(competitionId: String)(id: String): F[Option[RegistrationPeriod]]

  def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): F[List[ScheduleEntry]]

  def getScheduleRequirementsByPeriodId(competitionId: String)(periodId: String): F[List[ScheduleRequirement]]

  def getPeriodsByCompetitionId(competitionId: String): F[List[Period]]

  def getPeriodById(competitionId: String)(id: String): F[Option[Period]]

  def getStagesByCategory(competitionId: String)(categoryId: String): F[List[StageDescriptor]]
  def getStageById(competitionId: String)(categoryId: String, id: String): F[Option[StageDescriptor]]
}

object CompetitionQueryOperations {
  def apply[F[+_]](implicit F: CompetitionQueryOperations[F]): CompetitionQueryOperations[F] = F

  def test(
    competitionProperties: Option[Ref[Map[String, CompetitionProperties]]] = None,
    categories: Option[Ref[Map[String, Category]]] = None,
    competitors: Option[Ref[Map[String, Competitor]]] = None,
    periods: Option[Ref[Map[String, Period]]] = None,
    registrationPeriods: Option[Ref[Map[String, RegistrationPeriod]]] = None,
    registrationGroups: Option[Ref[Map[String, RegistrationGroup]]] = None,
    stages: Option[Ref[Map[String, StageDescriptor]]] = None
  ): CompetitionQueryOperations[LIO] = new CompetitionQueryOperations[LIO] with CommonTestOperations {

    override def getCompetitionProperties(id: String): LIO[Option[CompetitionProperties]] =
      getById(competitionProperties)(id)

    override def getCategoriesByCompetitionId(competitionId: String): LIO[List[Category]] = {
      categories match {
        case Some(value) => value.get.map(_.values.filter(_.competitionId == competitionId).toList)
        case None        => Task(List.empty)
      }
    }

    override def getCompetitionInfoTemplate(competitionId: String): LIO[Option[CompetitionInfoTemplate]] =
      getCompetitionProperties(competitionId).map(_.map(_.infoTemplate))

    override def getCategoryById(competitionId: String)(id: String): LIO[Option[Category]] = getById(categories)(id)

    override def searchCategory(
      competitionId: String
    )(searchString: String, pagination: Option[Pagination]): LIO[(List[Category], Pagination)] =
      getCategoriesByCompetitionId(competitionId).map(cats => (cats, Pagination(0, cats.size, cats.size)))

    override def getCompetitorById(competitionId: String)(id: String): LIO[Option[Competitor]] =
      getById(competitors)(id)

    override def getCompetitorsByCategoryId(competitionId: String)(
      categoryId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = competitors match {
      case Some(value) => value.get.map(_.values.toList.filter(_.categories.contains(categoryId)))
          .map(list => (list, Pagination(0, list.size, list.size)))
      case None => Task((List.empty, Pagination(0, 0, 0)))
    }

    override def getCompetitorsByCompetitionId(
      competitionId: String
    )(pagination: Option[Pagination], searchString: Option[String]): LIO[(List[Competitor], Pagination)] = {
      competitors match {
        case Some(value) => value.get.map(_.values.toList.filter(_.competitionId.equals(competitionId)))
            .map(list => (list, Pagination(0, list.size, list.size)))
        case None => Task((List.empty, Pagination(0, 0, 0)))
      }
    }

    override def getCompetitorsByAcademyId(competitionId: String)(
      academyId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = competitors match {
      case Some(value) => value.get.map(_.values.toList.filter(_.academy.exists(_.academyId == academyId)))
          .map(list => (list, Pagination(0, list.size, list.size)))
      case None => Task((List.empty, Pagination(0, 0, 0)))
    }

    override def getRegistrationGroups(competitionId: String): LIO[List[RegistrationGroup]] = registrationGroups match {
      case Some(value) => value.get.map(_.values.toList.filter(_.competitionId.eq(competitionId)))
      case None        => Task(List.empty)
    }

    override def getRegistrationGroupById(competitionId: String)(id: String): LIO[Option[RegistrationGroup]] =
      getById(registrationGroups)(id)

    override def getRegistrationPeriods(competitionId: String): LIO[List[RegistrationPeriod]] =
      registrationPeriods match {
        case Some(value) => value.get.map(_.values.toList.filter(_.competitionId.eq(competitionId)))
        case None        => Task(List.empty)
      }

    override def getRegistrationPeriodById(competitionId: String)(id: String): LIO[Option[RegistrationPeriod]] =
      getById(registrationPeriods)(id)

    override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): LIO[List[ScheduleEntry]] =
      getPeriodById(competitionId)(periodId).map(_.map(_.scheduleEntries).getOrElse(List.empty))

    override def getScheduleRequirementsByPeriodId(
      competitionId: String
    )(periodId: String): LIO[List[ScheduleRequirement]] = getPeriodById(competitionId)(periodId)
      .map(_.map(_.scheduleRequirements).getOrElse(List.empty))

    override def getPeriodsByCompetitionId(competitionId: String): LIO[List[Period]] = periods match {
      case Some(value) => value.get.map(_.values.toList.filter(_.competitionId.eq(competitionId)))
      case None        => Task(List.empty)
    }

    override def getPeriodById(competitionId: String)(id: String): LIO[Option[Period]] = getById(periods)(id)

    override def getStagesByCategory(competitionId: String)(categoryId: String): LIO[List[StageDescriptor]] =
      getStagesByCategory(stages)(competitionId)(categoryId)

    override def getStageById(competitionId: String)(cagtegoryId: String, id: String): LIO[Option[StageDescriptor]] =
      getById(stages)(id)

    override def getNumberOfCompetitorsForCategory(competitionId: String)(categoryId: String): LIO[Int] = (for {
      cmtrs <- competitors
      result = cmtrs.get.map(_.values.count(_.categories.contains(categoryId)))
    } yield result).getOrElse(ZIO.effectTotal(0))
  }

  def live(mongo: MongoClient, name: String)(implicit
    log: CompetitionLogging.Service[LIO]
  ): CompetitionQueryOperations[LIO] = new CompetitionQueryOperations[LIO] with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def idField: String = "id"

    override def getCompetitionProperties(id: String): LIO[Option[CompetitionProperties]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(equal(idField, id)).map(_.properties)
        res <- selectOne(select)
      } yield res
    }

    override def getCategoriesByCompetitionId(competitionId: String): LIO[List[Category]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(equal(idField, competitionId)).map(_.categories)
        res <- RIO.fromFuture(_ => select.head())
      } yield res.values.toList
    }

    override def getCompetitionInfoTemplate(competitionId: String): LIO[Option[CompetitionInfoTemplate]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(equal(idField, competitionId)).map(_.properties.infoTemplate)
        res <- selectOne(select)
      } yield res
    }

    override def getCategoryById(competitionId: String)(id: String): LIO[Option[Category]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId), exists(s"categories.$id")))
        res <- RIO.fromFuture(_ => select.headOption())
      } yield res.flatMap(_.categories.get(id))
    }

    override def searchCategory(
      competitionId: String
    )(searchString: String, pagination: Option[Pagination]): LIO[(List[Category], Pagination)] = {
      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(30)
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId)))
        res <- RIO.fromFuture(_ => select.head())
      } yield (
        res.categories.filter(c =>
          searchString == null || searchString.isBlank ||
            c._2.restrictions.exists(_.name.exists(_.matches(searchString)))
        ).slice(drop, drop + take).values.toList,
        pagination.getOrElse(Pagination(0, res.categories.size, res.categories.size))
      )
    }

    override def getCompetitorById(competitionId: String)(id: String): LIO[Option[Competitor]] = {
      for {
        collection <- competitorCollection
        select = collection.find(and(equal("competitionId", competitionId), equal(idField, id)))
        res <- RIO.fromFuture(_ => select.headOption())
      } yield res
    }

    private def selectWithPagination[T](
      select: FindObservable[T],
      pagination: Option[Pagination],
      total: Future[Long]
    ) = {
      for {
        res <- RIO.fromFuture(_ => select.toFuture())
        tr  <- RIO.fromFuture(_ => total)
      } yield (res.toList, pagination.map(_.copy(totalResults = tr.toInt)).getOrElse(Pagination(0, res.size, tr.toInt)))
    }
    override def getCompetitorsByCategoryId(competitionId: String)(
      categoryId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = {
      val drop   = pagination.map(_.offset).getOrElse(0)
      val take   = pagination.map(_.maxResults).getOrElse(0)
      val filter = and(equal("competitionId", competitionId), equal("categories", categoryId))
      for {
        collection <- competitorCollection
        select = collection.find(filter).skip(drop).limit(take)
        total  = collection.countDocuments(filter).toFuture()
        res <- selectWithPagination(select, pagination, total)
      } yield res
    }

    override def getCompetitorsByCompetitionId(
      competitionId: String
    )(pagination: Option[Pagination], searchString: Option[String]): LIO[(List[Competitor], Pagination)] = {

      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(0)
      for {
        collection <- competitorCollection
        select = collection.find(equal("competitionId", competitionId)).skip(drop).limit(take)
        total  = collection.countDocuments(equal("competitionId", competitionId)).toFuture()
        res <- selectWithPagination(select, pagination, total)
      } yield res
    }

    override def getCompetitorsByAcademyId(competitionId: String)(
      academyId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = {
      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(0)
      for {
        collection <- competitorCollection
        select = collection.find(and(equal("competitionId", competitionId), equal("academy.id", academyId))).skip(drop)
          .limit(take)
        total = collection.countDocuments(equal("competitionId", competitionId)).toFuture()
        res <- selectWithPagination(select, pagination, total)
      } yield res
    }

    private def getStateById(competitionId: String) = {
      for {
        collection <- competitionStateCollection
        select = collection.find(equal(idField, competitionId))
        res <- RIO.fromFuture(_ => select.headOption())
      } yield res
    }

    override def getRegistrationGroups(competitionId: String): LIO[List[RegistrationGroup]] = {
      for {
        collection <- competitorCollection
        res <- getStateById(competitionId)
          .map(_.flatMap(_.registrationInfo).map(_.registrationGroups.values.toList).getOrElse(List.empty))
      } yield res
    }

    override def getRegistrationGroupById(competitionId: String)(id: String): LIO[Option[RegistrationGroup]] = {
      getStateById(competitionId).map(_.flatMap(_.registrationInfo).flatMap(_.registrationGroups.get(id)))
    }

    override def getRegistrationPeriods(competitionId: String): LIO[List[RegistrationPeriod]] = {
      getStateById(competitionId)
        .map(_.flatMap(_.registrationInfo).map(_.registrationPeriods.values.toList).getOrElse(List.empty))
    }

    override def getRegistrationPeriodById(competitionId: String)(id: String): LIO[Option[RegistrationPeriod]] = {
      getStateById(competitionId).map(_.flatMap(_.registrationInfo).flatMap(_.registrationPeriods.get(id)))
    }

    override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): LIO[List[ScheduleEntry]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods.get(periodId).map(_.scheduleEntries).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getScheduleRequirementsByPeriodId(
      competitionId: String
    )(periodId: String): LIO[List[ScheduleRequirement]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods.get(periodId).map(_.scheduleRequirements).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getPeriodsByCompetitionId(competitionId: String): LIO[List[Period]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods.values.toList
        case None        => List.empty
      }
    }

    override def getPeriodById(competitionId: String)(id: String): LIO[Option[Period]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods.get(id)
        case None        => None
      }
    }

    override def getStagesByCategory(competitionId: String)(categoryId: String): LIO[List[StageDescriptor]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId)))
        res <- RIO.fromFuture(_ => select.headOption())
      } yield res match {
        case Some(value) => value.stages.values.filter(_.categoryId == categoryId).toList
        case None        => List.empty
      }
    }

    override def getStageById(competitionId: String)(categoryId: String, id: String): LIO[Option[StageDescriptor]] = {
      for {
        collection <- competitionStateCollection
        select = collection.find(and(equal(idField, competitionId), exists(s"stages.$id")))
        res <- RIO.fromFuture(_ => select.headOption())
      } yield res match {
        case Some(value) => value.stages.get(id)
        case None        => None
      }
    }

    override def getNumberOfCompetitorsForCategory(competitionId: String)(categoryId: String): LIO[Int] = {
      for {
        collection <- competitorCollection
        select = collection.countDocuments(and(equal("competitionId", competitionId), equal("categories", categoryId)))
        res <- RIO.fromFuture(_ => select.toFuture()).map(_.toInt)
      } yield res
    }
  }

  private def selectOne[T](select: Observable[T])(implicit log: CompetitionLogging.Service[LIO]) = {
    for { res <- RIO.fromFuture(_ => select.headOption()) } yield res
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

  def getRegistrationGroups[F[+_]: CompetitionQueryOperations](competitionId: String): F[List[RegistrationGroup]] =
    CompetitionQueryOperations[F].getRegistrationGroups(competitionId)

  def getRegistrationGroupById[F[+_]: CompetitionQueryOperations](competitionId: String)(
    id: String
  ): F[Option[RegistrationGroup]] = CompetitionQueryOperations[F].getRegistrationGroupById(competitionId)(id)

  def getRegistrationPeriods[F[+_]: CompetitionQueryOperations](competitionId: String): F[List[RegistrationPeriod]] =
    CompetitionQueryOperations[F].getRegistrationPeriods(competitionId)

  def getRegistrationPeriodById[F[+_]: CompetitionQueryOperations](competitionId: String)(
    id: String
  ): F[Option[RegistrationPeriod]] = CompetitionQueryOperations[F].getRegistrationPeriodById(competitionId)(id)

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
