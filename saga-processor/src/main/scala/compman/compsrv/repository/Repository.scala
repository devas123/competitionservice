package compman.compsrv.repository

import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CompetitionPropertiesDTO, CompetitorDTO, FightDescriptionDTO}
import compman.compsrv.model.Errors

trait CompetitionRepository[F[_]] {
  def getCompetition(
      competitionId: String,
      getForUpdate: Boolean = false
  ): F[Either[Errors.Error, CompetitionPropertiesDTO]]
  def putCompetition(competition: CompetitionPropertiesDTO): F[Unit]
  def competitionExists(competitionId: String): F[Boolean]
  def deleteCompetition(competitionId: String): F[Unit]
}

object CompetitionRepository {
  def apply[F[_]](implicit F: CompetitionRepository[F]): CompetitionRepository[F] = F
}

trait FightRepository[F[_]] {
  def getFights(
      fightIds: Seq[String],
      getForUpdate: Boolean = false
  ): F[Seq[FightDescriptionDTO]]
  def getFight(
      fightId: String,
      getForUpdate: Boolean = false
  ): F[Either[Errors.Error, FightDescriptionDTO]]
  def deleteFight(id: String): F[Unit]
  def putFight(fight: FightDescriptionDTO): F[Unit]
  def fightsCount(categoryIds: Seq[String], competitorId: String): F[Int]
  def getCompetitionFights(
      competitionId: String,
      getForUpdate: Boolean = false
  ): F[Seq[FightDescriptionDTO]]
}

object FightRepository {
  def apply[F[_]](implicit F: FightRepository[F]): FightRepository[F] = F
}

trait CompetitorRepository[F[_]] {
  def getCompetitor(
      competitorId: String,
      getForUpdate: Boolean = false
  ): F[Either[Errors.Error, CompetitorDTO]]
  def getCompetitors(competitorIds: Seq[String]): F[Seq[CompetitorDTO]]
  def getCompetitionCompetitors(
      competitionId: String,
      getForUpdate: Boolean = false
  ): F[Seq[CompetitorDTO]]
  def competitorExists(competitorId: String): F[Boolean]
  def getCategoryCompetitors(
      categoryId: String,
      getForUpdate: Boolean = false
  ): F[Seq[CompetitorDTO]]
  def putCompetitor(competitor: CompetitorDTO): F[Unit]
  def deleteCompetitor(id: String): F[Unit]

}
object CompetitorRepository {
  def apply[F[_]](implicit F: CompetitorRepository[F]): CompetitorRepository[F] = F
}

trait CategoryRepository[F[_]] {
  def getCategory(
      categoryId: String,
      getForUpdate: Boolean = false
  ): F[Either[Errors.Error, CategoryDescriptorDTO]]
  def getCategories(
      categoryIds: Seq[String],
      getForUpdate: Boolean = false
  ): F[Seq[CategoryDescriptorDTO]]

  def putCategory(category: CategoryDescriptorDTO): F[Unit]
  def categoryExists(categoryId: String): F[Boolean]
  def deleteCategory(categoryId: String, competitionId: String): F[Unit]

}

object CategoryRepository {
  def apply[F[_]](implicit F: CategoryRepository[F]): CategoryRepository[F] = F
}

trait TransactionOperations[F[_]] {
  def commit(): F[Unit]
  def rollback(): F[Unit]
}

object TransactionOperations {
  def apply[F[_]](implicit F: TransactionOperations[F]): TransactionOperations[F] = F
}
