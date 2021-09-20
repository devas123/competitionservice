package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import io.getquill.{CassandraZioContext, CassandraZioSession, EntityQuery, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.{Has, Task}

trait CompetitionQueryOperations[F[+_]] {
  def getCompetitionProperties(id: String): F[Option[CompetitionProperties]]

  def getCategoriesByCompetitionId(competitionId: String): F[List[Category]]

  def getCompetitionInfoTemplate(competitionId: String): F[Option[CompetitionInfoTemplate]]

  def getCategoryById(competitionId: String)(id: String): F[Option[Category]]

  def searchCategory(
    competitionId: String
  )(searchString: String, pagination: Option[Pagination]): F[(List[Category], Pagination)]

  def getFightsByMat(competitionId: String)(matId: String): F[List[Fight]]

  def getFightsByStage(competitionId: String)(stageId: String): F[List[Fight]]

  def getFightById(competitionId: String)(id: String): F[Option[Fight]]
  def getFightsByIds(competitionId: String)(ids: Set[String]): F[List[Fight]]

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
  def getStageById(competitionId: String)(id: String): F[Option[StageDescriptor]]
}

object CompetitionQueryOperations {
  def apply[F[+_]](implicit F: CompetitionQueryOperations[F]): CompetitionQueryOperations[F] = F

  def test: CompetitionQueryOperations[LIO] = new CompetitionQueryOperations[LIO] {
    override def getCompetitionProperties(id: String): LIO[Option[CompetitionProperties]] = ???

    override def getCategoriesByCompetitionId(competitionId: String): LIO[List[Category]] = ???

    override def getCompetitionInfoTemplate(competitionId: String): LIO[Option[CompetitionInfoTemplate]] = ???

    override def getCategoryById(competitionId: String)(id: String): LIO[Option[Category]] = ???

    override def searchCategory(competitionId: String)(searchString: String, pagination: Option[Pagination]): LIO[(List[Category], Pagination)] = ???

    override def getFightsByMat(competitionId: String)(matId: String): LIO[List[Fight]] = ???

    override def getFightsByStage(competitionId: String)(stageId: String): LIO[List[Fight]] = ???

    override def getFightById(competitionId: String)(id: String): LIO[Option[Fight]] = ???

    override def getFightsByIds(competitionId: String)(ids: Set[String]): LIO[List[Fight]] = ???

    override def getCompetitorById(competitionId: String)(id: String): LIO[Option[Competitor]] = ???

    override def getCompetitorsByCategoryId(competitionId: String)(categoryId: String, pagination: Option[Pagination], searchString: Option[String]): LIO[(List[Competitor], Pagination)] = ???

    override def getCompetitorsByCompetitionId(competitionId: String)(pagination: Option[Pagination], searchString: Option[String]): LIO[(List[Competitor], Pagination)] = ???

    override def getCompetitorsByAcademyId(competitionId: String)(academyId: String, pagination: Option[Pagination], searchString: Option[String]): LIO[(List[Competitor], Pagination)] = ???

    override def getRegistrationGroups(competitionId: String): LIO[List[RegistrationGroup]] = ???

    override def getRegistrationGroupById(competitionId: String)(id: String): LIO[Option[RegistrationGroup]] = ???

    override def getRegistrationPeriods(competitionId: String): LIO[List[RegistrationPeriod]] = ???

    override def getRegistrationPeriodById(competitionId: String)(id: String): LIO[Option[RegistrationPeriod]] = ???

    override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): LIO[List[ScheduleEntry]] = ???

    override def getScheduleRequirementsByPeriodId(competitionId: String)(periodId: String): LIO[List[ScheduleRequirement]] = ???

    override def getPeriodsByCompetitionId(competitionId: String): LIO[List[Period]] = ???

    override def getPeriodById(competitionId: String)(id: String): LIO[Option[Period]] = ???

    override def getStagesByCategory(competitionId: String)(categoryId: String): LIO[List[StageDescriptor]] = ???

    override def getStageById(competitionId: String)(id: String): LIO[Option[StageDescriptor]] = ???
  }

  def live(cassandraZioSession: CassandraZioSession)(implicit log: CompetitionLogging.Service[LIO]): CompetitionQueryOperations[LIO] =
    new CompetitionQueryOperations[LIO] {
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
          _   <- log.info(select.toString)
          res <- run(select).provide(Has(cassandraZioSession))
          filtered = res.filter(c =>
            searchString.isEmpty || searchString.exists(s => c.firstName.contains(s) || c.lastName.contains(s))
          )
        } yield (filtered.slice(drop, drop + take), Pagination(drop, take, filtered.size))
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

      override def getFightsByMat(competitionId: String)(matId: String): LIO[List[Fight]] = {
        val select = quote {
          query[Fight].filter(f => f.competitionId == lift(competitionId) && f.scheduleInfo.matId.contains(lift(matId)))
        }
        for {
          _   <- log.info(select.toString)
          res <- run(select).provide(Has(cassandraZioSession))
        } yield res
      }

      override def getFightsByStage(competitionId: String)(stageId: String): LIO[List[Fight]] = {
        val select =
          quote { query[Fight].filter(f => f.competitionId == lift(competitionId) && f.stageId == lift(stageId)) }
        for {
          _   <- log.info(select.toString)
          res <- run(select).provide(Has(cassandraZioSession))
        } yield res
      }

      override def getFightById(competitionId: String)(id: String): LIO[Option[Fight]] = {
        val select = quote { query[Fight].filter(f => f.competitionId == lift(competitionId) && f.id == lift(id)) }
        for {
          _   <- log.info(select.toString)
          res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
        } yield res
      }

      override def getFightsByIds(competitionId: String)(ids: Set[String]): LIO[List[Fight]] = {
        val select = quote(query[Fight]).dynamic
          .filterIf(ids.nonEmpty)(f => quote(f.competitionId == lift(competitionId) && liftQuery(ids).contains(f.id)))
        for {
          _   <- log.info(select.toString)
          res <- run(select).provide(Has(cassandraZioSession))
        } yield res
      }

      override def getCompetitorById(competitionId: String)(id: String): LIO[Option[Competitor]] = {
        val select = quote { query[Competitor].filter(f => f.competitionId == lift(competitionId) && f.id == lift(id)) }
        for {
          _   <- log.info(select.toString)
          res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
        } yield res
      }

      override def getCompetitorsByCategoryId(competitionId: String)(
        categoryId: String,
        pagination: Option[Pagination],
        searchString: Option[String]
      ): LIO[(List[Competitor], Pagination)] = {
        val drop = pagination.map(_.offset).getOrElse(0)
        val take = pagination.map(_.maxResults).getOrElse(30)
        val select = quote {
          query[Competitor]
            .filter(f => f.competitionId == lift(competitionId) && f.categories.contains(lift(categoryId)))
            .allowFiltering
        }
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
            .filter(f => f.competitionId == lift(competitionId) && f.academy.exists(_.id == lift(academyId)))
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

      private def periodQuery(competitionId: String, periodId: String) = quote {
        query[Period]
          .filter(rg => rg.competitionId == lift(competitionId) && rg.id == lift(periodId))
      }

      override def getScheduleEntriesByPeriodId(
        competitionId: String
      )(periodId: String): LIO[List[ScheduleEntry]] = {
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

      override def getStageById(competitionId: String)(id: String): LIO[Option[StageDescriptor]] = {
        val select =
          quote { query[StageDescriptor].filter(rg => rg.competitionId == lift(competitionId) && rg.id == lift(id)) }
        for {
          _   <- log.info(select.toString)
          res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
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

  def getFightsByMat[F[+_]: CompetitionQueryOperations](competitionId: String)(matId: String): F[List[Fight]] =
    CompetitionQueryOperations[F].getFightsByMat(competitionId)(matId)

  def getFightsByStage[F[+_]: CompetitionQueryOperations](competitionId: String)(stageId: String): F[List[Fight]] =
    CompetitionQueryOperations[F].getFightsByStage(competitionId)(stageId)

  def getFightById[F[+_]: CompetitionQueryOperations](competitionId: String)(id: String): F[Option[Fight]] =
    CompetitionQueryOperations[F].getFightById(competitionId)(id)

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
