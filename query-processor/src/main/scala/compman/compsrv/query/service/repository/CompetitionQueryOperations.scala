package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import io.getquill.{CassandraZioContext, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}

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
  def getFightsByIds(competitionId: String)(ids: Seq[String]): F[List[Fight]]

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

  def live(implicit log: CompetitionLogging.Service[LIO]): CompetitionQueryOperations[RepoIO] =
    new CompetitionQueryOperations[RepoIO] {
      private lazy val ctx =
        new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders

      import ctx._
      override def getCompetitionProperties(id: String): RepoIO[Option[CompetitionProperties]] = {
        val select = quote { query[CompetitionProperties].filter(_.id == lift(id)) }
        for {
          _   <- log.info(select.toString)
          res <- run(select).map(_.headOption)
        } yield res
      }

      override def getCategoriesByCompetitionId(competitionId: String): RepoIO[List[Category]] = ???

      override def getCompetitionInfoTemplate(competitionId: String): RepoIO[Option[CompetitionInfoTemplate]] = ???

      override def getCategoryById(competitionId: String)(id: String): RepoIO[Option[Category]] = ???

      override def searchCategory(
        competitionId: String
      )(searchString: String, pagination: Option[Pagination]): RepoIO[(List[Category], Pagination)] = ???

      override def getFightsByMat(competitionId: String)(matId: String): RepoIO[List[Fight]] = ???

      override def getFightsByStage(competitionId: String)(stageId: String): RepoIO[List[Fight]] = ???

      override def getFightById(competitionId: String)(id: String): RepoIO[Option[Fight]] = ???

      override def getFightsByIds(competitionId: String)(ids: Seq[String]): RepoIO[List[Fight]] = ???

      override def getCompetitorById(competitionId: String)(id: String): RepoIO[Option[Competitor]] = ???

      override def getCompetitorsByCategoryId(competitionId: String)(
        categoryId: String,
        pagination: Option[Pagination],
        searchString: Option[String]
      ): RepoIO[(List[Competitor], Pagination)] = ???

      override def getCompetitorsByCompetitionId(
        competitionId: String
      )(pagination: Option[Pagination], searchString: Option[String]): RepoIO[(List[Competitor], Pagination)] = ???

      override def getCompetitorsByAcademyId(competitionId: String)(
        academyId: String,
        pagination: Option[Pagination],
        searchString: Option[String]
      ): RepoIO[(List[Competitor], Pagination)] = ???

      override def getRegistrationGroups(competitionId: String): RepoIO[List[RegistrationGroup]] = ???

      override def getRegistrationGroupById(competitionId: String)(id: String): RepoIO[Option[RegistrationGroup]] = ???

      override def getRegistrationPeriods(competitionId: String): RepoIO[List[RegistrationPeriod]] = ???

      override def getRegistrationPeriodById(competitionId: String)(id: String): RepoIO[Option[RegistrationPeriod]] =
        ???

      override def getScheduleEntriesByPeriodId(competitionId: String)(periodId: String): RepoIO[List[ScheduleEntry]] =
        ???

      override def getScheduleRequirementsByPeriodId(
        competitionId: String
      )(periodId: String): RepoIO[List[ScheduleRequirement]] = ???

      override def getPeriodsByCompetitionId(competitionId: String): RepoIO[List[Period]] = ???

      override def getPeriodById(competitionId: String)(id: String): RepoIO[Option[Period]] = ???

      override def getStagesByCategory(competitionId: String)(categoryId: String): RepoIO[List[StageDescriptor]] = ???

      override def getStageById(competitionId: String)(id: String): RepoIO[Option[StageDescriptor]] = ???
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
