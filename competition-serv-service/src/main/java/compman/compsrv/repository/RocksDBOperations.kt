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
                { mapper.readValue(it.get(columnFamilyHandle, id.toByteArray()), T::class.java) })
    }

    private inline fun <reified T> getWithTransaction(getForUpdate: Boolean, tr: Transaction, columnFamilyHandle: ColumnFamilyHandle, id: String): T {
        val readOptions = ReadOptions()
        val bytes = if (getForUpdate) {
            tr.getForUpdate(readOptions, columnFamilyHandle, id.toByteArray(), false)
        } else {
            tr.get(columnFamilyHandle, readOptions, id.toByteArray())
        }
        return mapper.readValue(bytes, T::class.java)
    }

    private inline fun <reified T> performMultiGet(id: List<String>, columnFamilyHandle: List<ColumnFamilyHandle>, getForUpdate: Boolean = false): List<T> {
        return db.fold({ transaction ->
            val readOptions = ReadOptions()
            val bytes = if (getForUpdate) {
                transaction.multiGetForUpdate(readOptions, columnFamilyHandle, id.map { it.toByteArray() }.toTypedArray())?.toList()
            } else {
                transaction.multiGet(readOptions, columnFamilyHandle, id.map { it.toByteArray() }.toTypedArray())?.toList()
            }
            bytes?.map { mapper.readValue(it, T::class.java) }.orEmpty()
        },
                { transactionDB -> transactionDB.multiGetAsList(ReadOptions(), columnFamilyHandle, id.map { it.toByteArray() })?.map { mapper.readValue(it, T::class.java) }.orEmpty() })
    }

    private inline fun <reified T> performPut(id: String, value: T, columnFamilyHandle: ColumnFamilyHandle) {
        db.fold({
            it.put(columnFamilyHandle, id.toByteArray(), mapper.writeValueAsBytes(value))
        },
                { it.put(columnFamilyHandle, id.toByteArray(), mapper.writeValueAsBytes(value)) })
    }

    override fun getCompetition(competitionId: String, getForUpdate: Boolean): Competition {
        return performGet(competitionId, competitions, getForUpdate) ?: error("No Competition with id $competitionId")
    }

    override fun getCompetitor(competitorId: String, getForUpdate: Boolean): Competitor {
        return performGet(competitorId, competitors, getForUpdate) ?: error("No Competition with id $competitorId")
    }

    override fun putCompetition(competition: Competition) {
        performPut(competition.id, competition, competitions)
    }

    override fun getCategory(categoryId: String, getForUpdate: Boolean): Category {
        return performGet(categoryId, categories, getForUpdate) ?: error("No category with id $categoryId")
    }

    override fun getCategories(categoryIds: List<String>, getForUpdate: Boolean): List<Category> {
        return performMultiGet(categoryIds, listOf(categories), getForUpdate)
    }

    override fun getCategoryCompetitors(categoryId: String, getForUpdate: Boolean): List<Competitor> {
        val category = performGet<Category>(categoryId, categories)
        val competitors = performMultiGet<Competitor>(category?.competitors.orEmpty().toList(), listOf(competitors), getForUpdate)
        return competitors.filter { it.competitorDTO.categories?.contains(categoryId) == true }
    }

    override fun putCategory(category: Category) {
        performPut(category.id, category, categories)
    }

    override fun getCompetitionFights(competitionId: String, getForUpdate: Boolean): Array<FightDescriptionDTO> {
        val competition = getCompetition(competitionId)
        val fights = competition.let { c ->
            db.fold({
                if (getForUpdate) {
                    it.multiGetForUpdate(ReadOptions(), listOf(categories), c.categories.map { cat -> cat.toByteArray() }.toTypedArray())?.toList()
                } else {
                    it.multiGet(ReadOptions(), listOf(categories), c.categories.map { cat -> cat.toByteArray() }.toTypedArray())?.toList()
                }
            },
                    { it.multiGetAsList(listOf(categories), c.categories.map { cat -> cat.toByteArray() }) })
                    ?.map { mapper.readValue(it, Category::class.java) }?.flatMap { it.fights.toList() }
        }
        return fights.orEmpty().toTypedArray()
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
            for (category in competition.categories) {
                val cat = getWithTransaction<Category>(true, tr, categories, category)
                tr.delete(categories, category.toByteArray())
                for (competitor in cat.competitors) {
                    tr.delete(competitors, competitor.toByteArray())
                }
            }
        }, { tr ->
            val competition = mapper.readValue<Competition>(tr.get(competitions, competitionId.toByteArray()))
            tr.delete(competitions, competitionId.toByteArray())
            for (category in competition.categories) {
                val cat = mapper.readValue<Category>(tr.get(categories, category.toByteArray()))
                tr.delete(categories, category.toByteArray())
                for (competitor in cat.competitors) {
                    tr.delete(competitors, competitor.toByteArray())
                }
            }
        })
    }

    override fun putCompetitor(competitor: Competitor) {
        db.fold({ tr ->
            tr.put(competitors, competitor.competitorDTO.id.toByteArray(), mapper.writeValueAsBytes(competitor))
            for (category in competitor.competitorDTO.categories) {
                val cat = getWithTransaction<Category>(true, tr, categories, category)
                tr.put(categories, category.toByteArray(), mapper.writeValueAsBytes(cat.copy(competitors = cat.competitors + competitor.competitorDTO.id)))
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
            val competition = getWithTransaction<Competition>(true, tr, competitions, competitionId)
            tr.put(competitions, competitionId.toByteArray(), mapper.writeValueAsBytes(competition.copy(categories = competition.categories.filter { it != categoryId }.toTypedArray())))
            tr.delete(categories, categoryId.toByteArray())
        }, { tr ->
            val competition = mapper.readValue<Competition>(tr.get(competitions, competitionId.toByteArray()))
            tr.put(competitions, competitionId.toByteArray(), mapper.writeValueAsBytes(competition.copy(categories = competition.categories.filter { it != categoryId }.toTypedArray())))
            tr.delete(categories, categoryId.toByteArray())
        })

    }
}