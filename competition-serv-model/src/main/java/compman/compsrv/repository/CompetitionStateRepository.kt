package compman.compsrv.repository

import compman.compsrv.model.competition.CompetitionState
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
open class CompetitionStateRepository(@Autowired private val mongoTemplate: MongoTemplate) {


    companion object {
        private const val EVENT_NUMBER = "eventNumber"
        private const val COMPETITION_ID = "competitionId"
    }

    init {
        if (!mongoTemplate.collectionExists(CompetitionState::class.java)) {
            mongoTemplate.createCollection(CompetitionState::class.java)
            mongoTemplate.indexOps(CompetitionState::class.java).ensureIndex(Index(EVENT_NUMBER, Sort.Direction.DESC))
        }
    }

    fun save(state: CompetitionState) = mongoTemplate.save(state)
    fun exists(competitionId: String): Boolean {
        val query = Query(Criteria(COMPETITION_ID).`is`(competitionId))
        return mongoTemplate.exists(query, CompetitionState::class.java)
    }

    fun getLatest(competitionId: String): CompetitionState? {
        val query = Query(Criteria(COMPETITION_ID).`is`(competitionId)).with(Sort(Sort.Direction.DESC, EVENT_NUMBER)).limit(1)
        val res = mongoTemplate.find(query, CompetitionState::class.java)
        //if there is no snapshot, it means that we didn't start the competition. So just create it manually.
        if (res.isEmpty()) {
            return null
        }
        return res[0]
    }
}
