package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import io.getquill.{CassandraZioContext, CassandraZioSession, EntityQuery, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.{Has, Ref, Task, ZIO}

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

  def live(cassandraZioSession: CassandraZioSession)(implicit
    log: CompetitionLogging.Service[LIO]
  ): CompetitionQueryOperations[LIO] = new CompetitionQueryOperations[LIO] {
    private lazy val ctx =
      new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders

    import ctx._

    private def executeQueryAndFilterResults(
      log: CompetitionLogging.Service[LIO],
      searchString: Option[String],
      drop: Int,
      take: Int,
      select: Quoted[EntityQuery[Competitor]]
    ) = {
      for {
        _   <- log.info(s"Drop: $drop, take: $take")
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
        filtered = res.filter(c =>
          searchString.isEmpty || searchString.exists(s => c.firstName.contains(s) || c.lastName.contains(s))
        )
        realTake = if (take <= 0) filtered.size - drop else take
      } yield (filtered.slice(drop, drop + realTake), Pagination(drop, realTake, filtered.size))
    }

    override def getCompetitionProperties(id: String): LIO[Option[CompetitionProperties]] = {
      val select = quote { query[CompetitionProperties].filter(_.id == lift(id)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).map(_.headOption).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getCategoriesByCompetitionId(competitionId: String): LIO[List[Category]] = {
      val select = quote { query[Category].filter(_.competitionId == lift(competitionId)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getCompetitionInfoTemplate(competitionId: String): LIO[Option[CompetitionInfoTemplate]] = {
      val select = quote { query[CompetitionProperties].filter(_.id == lift(competitionId)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).map(_.headOption.map(_.infoTemplate)).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getCategoryById(competitionId: String)(id: String): LIO[Option[Category]] = {
      val select = quote { query[Category].filter(c => c.competitionId == lift(competitionId) && c.id == lift(id)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).map(_.headOption).provide(Has(cassandraZioSession))
      } yield res
    }

    override def searchCategory(
      competitionId: String
    )(searchString: String, pagination: Option[Pagination]): LIO[(List[Category], Pagination)] = {
      val drop   = pagination.map(_.offset).getOrElse(0)
      val take   = pagination.map(_.maxResults).getOrElse(30)
      val select = quote { query[Category].filter(c => c.competitionId == lift(competitionId)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
        filtered = res.filter(c => c.name.exists(_.contains(searchString)))
        resSize  = filtered.size
      } yield (filtered.slice(drop, drop + take), Pagination(drop, take, resSize))
    }

    override def getCompetitorById(competitionId: String)(id: String): LIO[Option[Competitor]] = {
      val select = quote { query[Competitor].filter(f => f.competitionId == lift(competitionId) && f.id == lift(id)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
      } yield res
    }

    val competitorsByCompetitionId: ctx.Quoted[String => EntityQuery[Competitor]] = quote { (competitionId: String) =>
      query[Competitor].filter(_.competitionId == competitionId)
    }

    val competitorsByCompetitionIdAndCategoryId: ctx.Quoted[(String, String) => EntityQuery[Competitor]] =
      quote { (competitionId: String, categoryId: String) =>
        competitorsByCompetitionId(competitionId).filter(_.categories.contains(categoryId)).allowFiltering
      }

    override def getCompetitorsByCategoryId(competitionId: String)(
      categoryId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = {
      val drop   = pagination.map(_.offset).getOrElse(0)
      val take   = pagination.map(_.maxResults).getOrElse(0)
      val select = quote(competitorsByCompetitionIdAndCategoryId(lift(competitionId), lift(categoryId)))
      executeQueryAndFilterResults(log, searchString, drop, take, select)
    }

    override def getCompetitorsByCompetitionId(
      competitionId: String
    )(pagination: Option[Pagination], searchString: Option[String]): LIO[(List[Competitor], Pagination)] = {
      val drop   = pagination.map(_.offset).getOrElse(0)
      val take   = pagination.map(_.maxResults).getOrElse(30)
      val select = quote { query[Competitor].filter(f => f.competitionId == lift(competitionId)) }
      executeQueryAndFilterResults(log, searchString, drop, take, select)
    }

    override def getCompetitorsByAcademyId(competitionId: String)(
      academyId: String,
      pagination: Option[Pagination],
      searchString: Option[String]
    ): LIO[(List[Competitor], Pagination)] = {
      val drop = pagination.map(_.offset).getOrElse(0)
      val take = pagination.map(_.maxResults).getOrElse(30)
      val select = quote {
        query[Competitor]
          .filter(f => f.competitionId == lift(competitionId) && f.academy.exists(_.academyId == lift(academyId)))
      }
      executeQueryAndFilterResults(log, searchString, drop, take, select)
    }

    override def getRegistrationGroups(competitionId: String): LIO[List[RegistrationGroup]] = {
      val select = quote { query[RegistrationGroup].filter(rg => rg.competitionId == lift(competitionId)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getRegistrationGroupById(competitionId: String)(id: String): LIO[Option[RegistrationGroup]] = {
      val select =
        quote { query[RegistrationGroup].filter(rg => rg.competitionId == lift(competitionId) && rg.id == lift(id)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
      } yield res
    }

    override def getRegistrationPeriods(competitionId: String): LIO[List[RegistrationPeriod]] = {
      val select = quote { query[RegistrationPeriod].filter(rg => rg.competitionId == lift(competitionId)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getRegistrationPeriodById(competitionId: String)(id: String): LIO[Option[RegistrationPeriod]] = {
      val select =
        quote { query[RegistrationPeriod].filter(rg => rg.competitionId == lift(competitionId) && rg.id == lift(id)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
      } yield res
    }

    private def periodQuery(competitionId: String, periodId: String) =
      quote { query[Period].filter(rg => rg.competitionId == lift(competitionId) && rg.id == lift(periodId)) }

    override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): LIO[List[ScheduleEntry]] = {
      val select = quote { periodQuery(competitionId, periodId).map(_.scheduleEntries) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.flatten)
      } yield res
    }

    override def getScheduleRequirementsByPeriodId(
      competitionId: String
    )(periodId: String): LIO[List[ScheduleRequirement]] = {
      val select = quote { periodQuery(competitionId, periodId).map(_.scheduleRequirements) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.flatten)
      } yield res
    }

    override def getPeriodsByCompetitionId(competitionId: String): LIO[List[Period]] = {
      val select = quote { query[Period].filter(rg => rg.competitionId == lift(competitionId)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getPeriodById(competitionId: String)(id: String): LIO[Option[Period]] = {
      val select = quote { periodQuery(competitionId, id) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
      } yield res
    }

    override def getStagesByCategory(competitionId: String)(categoryId: String): LIO[List[StageDescriptor]] = {
      val select = quote {
        query[StageDescriptor]
          .filter(rg => rg.competitionId == lift(competitionId) && rg.categoryId == lift(categoryId))
      }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getStageById(competitionId: String)(categoryId: String, id: String): LIO[Option[StageDescriptor]] = {
      val select = quote {
        query[StageDescriptor].filter(rg =>
          rg.competitionId == lift(competitionId) && rg.categoryId == lift(categoryId) && rg.id == lift(id)
        )
      }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
      } yield res
    }

    override def getNumberOfCompetitorsForCategory(competitionId: String)(categoryId: String): LIO[Int] = {
      val select = quote {
        query[Competitor].filter(f => f.competitionId == lift(competitionId) && f.categories.contains(lift(categoryId)))
          .map(_.id).allowFiltering
      }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res.size
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
