package compman.compsrv.repository

import arrow.core.Either
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import compman.compsrv.aggregate.Category
import compman.compsrv.aggregate.Competition
import compman.compsrv.aggregate.Competitor
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import org.rocksdb.*

open class RocksDBOperations(private val db: Either<Transaction, OptimisticTransactionDB>,
                             private val mapper: ObjectMapper,
                             private val competitors: ColumnFamilyHandle,
                             private val competitions: ColumnFamilyHandle,
                             private val categories: ColumnFamilyHandle) : DBOperations {


    private inline fun <reified T> performGet(id: String, columnFamilyHandle: ColumnFamilyHandle, getForUpdate: Boolean = false): T? {
        return db.fold({
            getWithTransaction<T>(getForUpdate, it, columnFamilyHandle, id)
        },
                { it.get(columnFamilyHandle, id.toByteArray())?.let { bytes -> mapper.readValue(bytes, T::class.java) }})
    }

    private inline fun <reified T> getWithTransaction(getForUpdate: Boolean, tr: Transaction, columnFamilyHandle: ColumnFamilyHandle, id: String): T? {
        val readOptions = ReadOptions()
        val bytes = if (getForUpdate) {
            tr.getForUpdate(readOptions, columnFamilyHandle, id.toByteArray(), false)
        } else {
            tr.get(columnFamilyHandle, readOptions, id.toByteArray())
        }
        return bytes?.let { mapper.readValue(it, T::class.java) }
    }

    private inline fun <reified T> performMultiGet(id: List<String>, columnFamilyHandle: ColumnFamilyHandle, getForUpdate: Boolean = false): List<T> {
        return db.fold({ transaction ->
            val readOptions = ReadOptions()
            val handles = List(id.size) { columnFamilyHandle }
            val bytes = if (getForUpdate) {
                transaction.multiGetForUpdate(readOptions, handles, id.map { it.toByteArray() }.toTypedArray())?.toList()
            } else {
                transaction.multiGet(readOptions, handles, id.map { it.toByteArray() }.toTypedArray())?.toList()
            }
            bytes?.map { mapper.readValue(it, T::class.java) }.orEmpty()
        },
                { transactionDB ->
                    val handles = List(id.size) { columnFamilyHandle }
                    transactionDB.multiGetAsList(ReadOptions(), handles, id.map { it.toByteArray() })?.map { mapper.readValue(it, T::class.java) }.orEmpty() })
    }

    private inline fun <reified T> performPut(id: String, value: T, columnFamilyHandle: ColumnFamilyHandle) {
        db.fold({
            it.put(columnFamilyHandle, id.toByteArray(), mapper.writeValueAsBytes(value))
        },
                { it.put(columnFamilyHandle, id.toByteArray(), mapper.writeValueAsBytes(value)) })
    }

    override fun getCompetition(competitionId: String, getForUpdate: Boolean): Competition {
        return performGet(competitionId, competitions, getForUpdate) ?: error("No competition with id $competitionId")
    }

    override fun getCompetitor(competitorId: String, getForUpdate: Boolean): Competitor {
        return performGet(competitorId, competitors, getForUpdate) ?: error("No competitor with id $competitorId")
    }

    override fun getCompetitors(competitorIds: List<String>): List<Competitor> {
        return performMultiGet(competitorIds, competitors, false)
    }

    override fun putCompetition(competition: Competition) {
        performPut(competition.id, competition, competitions)
    }

    override fun getCategory(categoryId: String, getForUpdate: Boolean): Category {
        return performGet(categoryId, categories, getForUpdate) ?: error("No category with id $categoryId")
    }

    override fun getCategories(categoryIds: List<String>, getForUpdate: Boolean): List<Category> {
        return performMultiGet(categoryIds, categories, getForUpdate)
    }

    override fun getCategoryCompetitors(categoryId: String, getForUpdate: Boolean): List<Competitor> {
        val category = performGet<Category>(categoryId, categories)
        val competitors = performMultiGet<Competitor>(category?.competitors.orEmpty().toList(), competitors, getForUpdate)
        return competitors.filter { it.competitorDTO.categories?.contains(categoryId) == true }
    }

    override fun putCategory(category: Category) {
        performPut(category.id, category, categories)
    }

    private fun getCategories(competition: Competition, getForUpdate: Boolean) = competition.let { c ->
        db.fold({
            if (getForUpdate) {
                it.multiGetForUpdate(
                    ReadOptions(),
                    List(c.categories.size) { categories },
                    c.categories.map { cat -> cat.toByteArray() }.toTypedArray()
                )?.toList()
            } else {
                it.multiGet(
                    ReadOptions(),
                    List(c.categories.size) { categories },
                    c.categories.map { cat -> cat.toByteArray() }.toTypedArray()
                )?.toList()
            }
        },
            { it.multiGetAsList(List(c.categories.size) { categories }, c.categories.map { cat -> cat.toByteArray() }) })
    }

    override fun getCompetitionFights(competitionId: String, getForUpdate: Boolean): Array<FightDescriptionDTO> {
        val competition = getCompetition(competitionId)
        val fights = getCategories(competition, getForUpdate)
                    ?.map { mapper.readValue(it, Category::class.java) }?.flatMap { it.fights.toList() }

        return fights.orEmpty().toTypedArray()
    }

    override fun getCompetitionCompetitors(competitionId: String, getForUpdate: Boolean): Array<Competitor> {
        val competition = getCompetition(competitionId, getForUpdate)
        val competitors = getCategories(competition, getForUpdate)
                ?.map { mapper.readValue(it, Category::class.java) }?.flatMap { performMultiGet<Competitor>(it.competitors.toList(), competitors) }
        return competitors.orEmpty().toTypedArray()
    }

    override fun categoryExists(categoryId: String): Boolean {
        return exists(categories, categoryId)
    }

    override fun competitorExists(competitorId: String): Boolean {
        return exists(competitors, competitorId)
    }

    override fun competitionExists(competitionId: String): Boolean {
        return exists(competitions, competitionId)
    }

    override fun exists(columnFamilyHandle: ColumnFamilyHandle, id: String): Boolean = db.fold({ it.get(columnFamilyHandle, ReadOptions(), id.toByteArray()) != null },
            { it.get(columnFamilyHandle, id.toByteArray(), ByteArray(1)) != RocksDB.NOT_FOUND })

    override fun commit() = db.fold({ it.commit() }, {})
    override fun rollback() = db.fold({ it.rollback() }, {})
    override fun fightsCount(categoryIds: List<String>, competitorId: String): Int {
        val categories = getCategories(categoryIds)
        return categories.fold(0) { acc, category -> acc + category.fights.count { f -> f.scores?.any { it.competitorId == competitorId } == true } }
    }

    override fun deleteCompetition(competitionId: String) {
        db.fold({ tr ->
            val competition = getWithTransaction<Competition>(true, tr, competitions, competitionId)
            tr.delete(competitions, competitionId.toByteArray())
            for (category in competition?.categories.orEmpty()) {
                val cat = getWithTransaction<Category>(true, tr, categories, category)
                tr.delete(categories, category.toByteArray())
                for (competitor in cat?.competitors.orEmpty()) {
                    tr.delete(competitors, competitor.toByteArray())
                }
            }
        }, { tr ->
            val competition = tr.get(competitions, competitionId.toByteArray())?.let { mapper.readValue(it, Competition::class.java) }
            tr.delete(competitions, competitionId.toByteArray())
            for (category in competition?.categories.orEmpty()) {
                val cat = tr.get(categories, category.toByteArray())?.let { mapper.readValue(it, Category::class.java) }
                tr.delete(categories, category.toByteArray())
                for (competitor in cat?.competitors.orEmpty()) {
                    tr.delete(competitors, competitor.toByteArray())
                }
            }
        })
    }

    override fun putCompetitor(competitor: Competitor) {
        db.fold({ tr ->
            tr.put(competitors, competitor.competitorDTO.id.toByteArray(), mapper.writeValueAsBytes(competitor))
            for (category in competitor.competitorDTO.categories) {
                getWithTransaction<Category>(true, tr, categories, category)?.let { cat ->
                    tr.put(
                        categories,
                        category.toByteArray(),
                        mapper.writeValueAsBytes(cat.copy(competitors = cat.competitors + competitor.competitorDTO.id))
                    )
                }
            }
        }, { tr ->
            tr.put(competitors, competitor.competitorDTO.id.toByteArray(), mapper.writeValueAsBytes(competitor))
            for (category in competitor.competitorDTO.categories) {
                val cat = tr.get(categories, category.toByteArray())?.let { mapper.readValue<Category>(it) }
                cat?.let { tr.put(categories, category.toByteArray(), mapper.writeValueAsBytes(it.copy(competitors = it.competitors + competitor.competitorDTO.id))) }
            }
        })
    }

    override fun deleteCategory(categoryId: String, competitionId: String) {
        db.fold({ tr ->
            getWithTransaction<Competition>(true, tr, competitions, competitionId)?.let { competition ->
                tr.put(
                    competitions,
                    competitionId.toByteArray(),
                    mapper.writeValueAsBytes(competition.copy(categories = competition.categories.filter { it != categoryId }
                        .toTypedArray()))
                )
            }
            tr.delete(categories, categoryId.toByteArray())
        }, { tr ->
            val competition = tr.get(competitions, competitionId.toByteArray())?.let { mapper.readValue<Competition>(it) }
            competition?.let { tr.put(competitions, competitionId.toByteArray(), mapper.writeValueAsBytes(competition.copy(categories = competition.categories.filter { it != categoryId }.toTypedArray()))) }
            tr.delete(categories, categoryId.toByteArray())
        })

    }

    override fun deleteCompetitor(id: String) {
        db.fold({ tr ->
            tr.delete(competitors, id.toByteArray())
        }, { tr ->
            tr.delete(competitors, id.toByteArray())
        })

    }
}