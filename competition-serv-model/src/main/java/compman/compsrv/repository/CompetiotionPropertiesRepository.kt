package compman.compsrv.repository

import compman.compsrv.model.competition.CompetitionProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
open class CompetiotionPropertiesRepository(private val mongoTemplate: MongoTemplate, private val crudRepository: CompetitionPropertiesCrudRepository) {
    companion object {
        const val COMPETITION_ID = "competitionId"
    }

    init {
        if (!mongoTemplate.collectionExists(CompetitionProperties::class.java)) {
            mongoTemplate.createCollection(CompetitionProperties::class.java)
            mongoTemplate.indexOps(CompetitionProperties::class.java).ensureIndex(Index().on(COMPETITION_ID, Sort.Direction.ASC).unique())
        }
    }

    fun save(prop: CompetitionProperties) = crudRepository.save(prop)

    fun find(competitionId: String): CompetitionProperties? {
        return crudRepository.findByCompetitionId(competitionId)
    }

    fun update(competitionId: String, propertyName: String, value: Any) {
        val query = Query(Criteria(COMPETITION_ID).`is`(competitionId))
        val update = Update().set(propertyName, value)
        mongoTemplate.updateFirst(query, update, CompetitionProperties::class.java)
    }
}
