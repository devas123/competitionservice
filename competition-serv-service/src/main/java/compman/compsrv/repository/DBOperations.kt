package compman.compsrv.repository

import compman.compsrv.aggregate.Category
import compman.compsrv.aggregate.Competition
import compman.compsrv.aggregate.Competitor
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import org.rocksdb.ColumnFamilyHandle

interface DBOperations {
    fun getCompetition(competitionId: String, getForUpdate: Boolean = false): Competition
    fun getCompetitor(competitorId: String, getForUpdate: Boolean = false): Competitor
    fun getCompetitors(competitorIds: List<String>): List<Competitor>
    fun putCompetition(competition: Competition)
    fun getCategory(categoryId: String, getForUpdate: Boolean = false): Category
    fun getCategories(categoryIds: List<String>, getForUpdate: Boolean = false): List<Category>
    fun getCategoryCompetitors(
        categoryId: String,
        getForUpdate: Boolean = false
    ): List<Competitor>

    fun putCategory(category: Category)
    fun getCompetitionFights(competitionId: String, getForUpdate: Boolean = false): Array<FightDescriptionDTO>
    fun getCompetitionCompetitors(competitionId: String, getForUpdate: Boolean = false): Array<Competitor>
    fun categoryExists(categoryId: String): Boolean
    fun competitorExists(competitorId: String): Boolean
    fun competitionExists(competitionId: String): Boolean
    fun exists(columnFamilyHandle: ColumnFamilyHandle, id: String): Boolean
    fun commit()
    fun rollback()
    fun fightsCount(categoryIds: List<String>, competitorId: String): Int
    fun deleteCompetition(competitionId: String)
    fun putCompetitor(competitor: Competitor)
    fun deleteCategory(categoryId: String, competitionId: String)
    fun deleteCompetitor(id: String)
}