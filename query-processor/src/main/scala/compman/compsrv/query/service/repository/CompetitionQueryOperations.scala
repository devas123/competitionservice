package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import org.mongodb.scala.{FindObservable, MongoClient, MongoCollection, Observable}
import org.mongodb.scala.model.Filters._
import zio.{Ref, RIO, Task, ZIO}

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
  ): CompetitionQueryOperations[LIO] = new CompetitionQueryOperations[LIO] with CommonOperations {

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

  def live(mongoClient: MongoClient, dbName: String)(implicit
    log: CompetitionLogging.Service[LIO]
  ): CompetitionQueryOperations[LIO] = new CompetitionQueryOperations[LIO] {

    private final val competitionStateCollectionName = "competition_state"

    private final val competitorsCollectionName = "competitor"

    private final val fightsCollectionName = "fight"

    import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
    import org.mongodb.scala.bson.codecs.Macros._
    import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

    private val codecRegistry = fromRegistries(
      fromProviders(classOf[CompetitionState]),
      fromProviders(classOf[Competitor]),
      fromProviders(classOf[Fight]),
      DEFAULT_CODEC_REGISTRY
    )
    private val database = mongoClient.getDatabase(dbName).withCodecRegistry(codecRegistry)
    private val competitionStateCollection: MongoCollection[CompetitionState] = database
      .getCollection(competitionStateCollectionName)
    private val competitorCollection: MongoCollection[Competitor] = database.getCollection(competitorsCollectionName)
    private val fightCollection: MongoCollection[Fight]           = database.getCollection(fightsCollectionName)

    private val idField = "id"

    override def getCompetitionProperties(id: String): LIO[Option[CompetitionProperties]] = {
      val select = competitionStateCollection.find(equal(idField, id)).map(_.properties)
      selectOne(select)
    }

    override def getCategoriesByCompetitionId(competitionId: String): LIO[List[Category]] = {
      val select = competitionStateCollection.find(equal(idField, competitionId)).map(_.categories)
      for {
        _   <- log.info(select.toString)
        res <- RIO.fromFuture(_ => select.head())
      } yield res
    }

    override def getCompetitionInfoTemplate(competitionId: String): LIO[Option[CompetitionInfoTemplate]] = {
      val select = competitionStateCollection.find(equal(idField, competitionId)).map(_.properties.infoTemplate)
      selectOne(select)
    }

    override def getCategoryById(competitionId: String)(id: String): LIO[Option[Category]] = {
      val select = competitionStateCollection.find(and(equal(idField, competitionId), equal("categories.id", id)))
        .map(_.categories)
      for {
        _   <- log.info(select.toString)
        res <- RIO.fromFuture(_ => select.head())
      } yield res.headOption
    }

    override def searchCategory(
      competitionId: String
    )(searchString: String, pagination: Option[Pagination]): LIO[(List[Category], Pagination)] = {
      val drop   = pagination.map(_.offset).getOrElse(0)
      val take   = pagination.map(_.maxResults).getOrElse(30)
      val select = competitionStateCollection.find(and(equal(idField, competitionId)))
      for {
        _   <- log.info(select.toString)
        res <- RIO.fromFuture(_ => select.head())
      } yield (
        res.categories.filter(c =>
          searchString == null || searchString.isBlank || c.restrictions.exists(_.name.exists(_.matches(searchString)))
        ).slice(drop, drop + take),
        pagination.getOrElse(Pagination(0, res.categories.size, res.categories.size))
      )
    }

    override def getCompetitorById(competitionId: String)(id: String): LIO[Option[Competitor]] = {
      val select = competitorCollection.find(and(equal("competitionId", competitionId), equal(idField, id)))
      for { res <- RIO.fromFuture(_ => select.headOption()) } yield res
    }

    private def selectWithPagination[T](select: FindObservable[T], pagination: Option[Pagination]) = {
      for { res <- RIO.fromFuture(_ => select.toFuture()) } yield (
        res.toList,
        pagination.getOrElse(Pagination(0, res.size, res.size))
      )
    }
    override def getCompetitorsByCategoryId(competitionId: String)(
      categoryId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = {
      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(0)
      val select = competitorCollection
        .find(and(equal("competitionId", competitionId), equal("categoryId", categoryId))).skip(drop).limit(take)
      selectWithPagination(select, pagination)
    }

    override def getCompetitorsByCompetitionId(
      competitionId: String
    )(pagination: Option[Pagination], searchString: Option[String]): LIO[(List[Competitor], Pagination)] = {
      val drop   = pagination.map(_.offset).getOrElse(0)
      val take   = pagination.map(_.maxResults).getOrElse(0)
      val select = competitorCollection.find(equal("competitionId", competitionId)).skip(drop).limit(take)
      selectWithPagination(select, pagination)
    }

    override def getCompetitorsByAcademyId(competitionId: String)(
      academyId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = {
      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(0)
      val select = competitorCollection.find(and(equal("competitionId", competitionId), equal("academy.id", academyId)))
        .skip(drop).limit(take)
      selectWithPagination(select, pagination)
    }

    private def getStateById(competitionId: String) = for {
      select <- RIO.effect(competitionStateCollection.find(equal(idField, competitionId)))
      res    <- RIO.fromFuture(_ => select.headOption())
    } yield res

    override def getRegistrationGroups(competitionId: String): LIO[List[RegistrationGroup]] = {
      getStateById(competitionId).map(_.flatMap(_.registrationInfo).map(_.registrationGroups).getOrElse(List.empty))
    }

    override def getRegistrationGroupById(competitionId: String)(id: String): LIO[Option[RegistrationGroup]] = {
      getStateById(competitionId).map(_.flatMap(_.registrationInfo).flatMap(_.registrationGroups.find(_.id == id)))
    }

    override def getRegistrationPeriods(competitionId: String): LIO[List[RegistrationPeriod]] = {
      getStateById(competitionId).map(_.flatMap(_.registrationInfo).map(_.registrationPeriods).getOrElse(List.empty))
    }

    override def getRegistrationPeriodById(competitionId: String)(id: String): LIO[Option[RegistrationPeriod]] = {
      getStateById(competitionId).map(_.flatMap(_.registrationInfo).flatMap(_.registrationPeriods.find(_.id == id)))
    }

    override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): LIO[List[ScheduleEntry]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods.find(_.id == periodId).map(_.scheduleEntries).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getScheduleRequirementsByPeriodId(
      competitionId: String
    )(periodId: String): LIO[List[ScheduleRequirement]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods.find(_.id == periodId).map(_.scheduleRequirements).getOrElse(List.empty)
        case None        => List.empty
      }
    }

    override def getPeriodsByCompetitionId(competitionId: String): LIO[List[Period]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods
        case None        => List.empty
      }
    }

    override def getPeriodById(competitionId: String)(id: String): LIO[Option[Period]] = {
      for { res <- getStateById(competitionId) } yield res match {
        case Some(value) => value.periods.find(_.id == id)
        case None        => None
      }
    }

    override def getStagesByCategory(competitionId: String)(categoryId: String): LIO[List[StageDescriptor]] = {
      val select = competitionStateCollection
        .find(and(equal(idField, competitionId), equal("stages.categoryId", categoryId)))
      for { res <- RIO.fromFuture(_ => select.headOption()) } yield res match {
        case Some(value) => value.stages.filter(_.categoryId == categoryId)
        case None        => List.empty
      }
    }

    override def getStageById(competitionId: String)(categoryId: String, id: String): LIO[Option[StageDescriptor]] = {
      val select = competitionStateCollection.find(and(equal(idField, competitionId), equal("stages.id", id)))
      for { res <- RIO.fromFuture(_ => select.headOption()) } yield res match {
        case Some(value) => value.stages.find(_.id == id)
        case None        => None
      }
    }

    override def getNumberOfCompetitorsForCategory(competitionId: String)(categoryId: String): LIO[Int] = {
      val select = competitorCollection
        .countDocuments(and(equal("competitionId", competitionId), equal("categoryId", categoryId)))
      RIO.fromFuture(_ => select.toFuture()).map(_.toInt)
    }
  }

  private def selectOne[T](select: Observable[T])(implicit log: CompetitionLogging.Service[LIO]) = {
    for {
      _   <- log.info(select.toString)
      res <- RIO.fromFuture(_ => select.headOption())
    } yield res
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
