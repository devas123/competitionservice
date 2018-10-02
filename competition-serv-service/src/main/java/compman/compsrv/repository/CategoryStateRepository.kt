package compman.compsrv.repository

import compman.compsrv.model.competition.CategoryState
import org.bson.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class CategoryStateRepository(@Autowired private val mongoTemplate: MongoTemplate) {


    companion object {
        private const val EVENT_NUMBER = "eventNumber"
        private const val CATEGORY_ID = "categoryId"
        private const val COLLECTION_NAME = "category-state"
        private const val SCHEDULE = "schedule"
        private val log: Logger = LoggerFactory.getLogger(CategoryStateRepository::class.java)
    }

    init {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME)
            mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(Index(EVENT_NUMBER, Sort.Direction.DESC))
            mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(CompoundIndexDefinition(Document.parse("{\"$EVENT_NUMBER\": 1, \"$CATEGORY_ID\": 1}")).unique())
        }
    }


    fun saveSnapshot(state: CategoryState) = try {
        mongoTemplate.save(state.createSnapshot(), COLLECTION_NAME)
    } catch (e: DuplicateKeyException) {
        log.info("No need to save, already have latest state in the repo")
    }

    fun getLatestSnapshot(categoryId: String): CategoryState? {
        val query = Query(Criteria(CATEGORY_ID).`is`(categoryId + "_SNAPSHOT")).with(Sort(Sort.Direction.DESC, EVENT_NUMBER)).limit(1)
        val res = mongoTemplate.find(query, CategoryState::class.java, COLLECTION_NAME)
        //if there is no snapshot, it means that we didn't start the competition. So just create it manually.
        if (res.isEmpty()) {
            return null
        }
        return res[0]
    }

    fun getLocalState(categoryId: String): CategoryState? {
        val query = Query(Criteria(CATEGORY_ID).`is`(categoryId)).limit(1)
        return mongoTemplate.findOne(query, CategoryState::class.java, COLLECTION_NAME)
    }

    fun setLocalState(state: CategoryState) {
        val query = Query(Criteria(CATEGORY_ID).`is`(state.category.categoryId))
        mongoTemplate.remove(query, COLLECTION_NAME)
        mongoTemplate.insert(state)
    }
}
