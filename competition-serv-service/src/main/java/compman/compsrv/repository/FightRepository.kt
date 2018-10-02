package compman.compsrv.repository

import compman.compsrv.model.competition.FightDescription
import compman.compsrv.model.es.events.FightStageUpdateEvent
import compman.compsrv.model.es.events.ScoreUpdateEvent
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component


@Component
class FightRepository(@Autowired private val mongoTemplate: MongoTemplate) {

    companion object {
        private const val COLLECTION_NAME = "fights"
        private const val FIGHT_ID = "fightId"
        private const val COMPETITION_ID = "competitionId"
        private const val COMPETITOR_ID = "competitorId"
        private const val CATEGORY_ID = "categoryId"
        private const val SCORES = "scores"
        private const val POINTS = "points"
        private const val ADVANTAGES = "advantages"
        private const val PENALTIES = "penalties"
        private const val WIN_FIGHT = "winFight"
        private const val LOSE_FIGHT = "loseFight"
        private const val COMPETITORS = "competitors"
        private const val STAGE = "stage"
        private const val FINISHED = "FINISHED"
        private const val PENDING = "PENDING"

        private val log = LoggerFactory.getLogger(FightRepository::class.java)

    }

    init {
        if (!mongoTemplate.collectionExists(COLLECTION_NAME)) {
            mongoTemplate.createCollection(COLLECTION_NAME)
            val c = CompoundIndexDefinition(Document.parse("{\"$FIGHT_ID\": 1, \"$COMPETITION_ID\": 1}")).unique()
            mongoTemplate.indexOps(COLLECTION_NAME).ensureIndex(c)
        }
    }

    fun overWriteCategory(catId: String, fights: Array<FightDescription>, competitionId: String) {
        mongoTemplate.remove(Query(Criteria(CATEGORY_ID).`is`(catId).and(COMPETITION_ID).`is`(competitionId)), FightDescription::class.java, COLLECTION_NAME)
        mongoTemplate.save(fights.asIterable(), COLLECTION_NAME)
    }

    fun save(fights: Array<FightDescription>) {
        mongoTemplate.insert(fights.toList(), COLLECTION_NAME)
    }


    private fun incrementRoundId(delta: Int, id: String): String {
        val idParts = id.split("-")
        val roundId = idParts[1].toInt()
        return StringBuilder(idParts[0]).append("-").append(roundId + delta).append("-").append(idParts[2]).toString()
    }

    fun putCompetitorsToFights(categoryId: String, fights: Array<FightDescription>, competitionId: String) {
        //find all the fights for category and check if they are empty
        val f = mongoTemplate.exists(Query(Criteria(CATEGORY_ID).`is`(categoryId).and(COMPETITION_ID).`is`(competitionId).and(COMPETITORS).not().size(0)), FightDescription::class.java, COLLECTION_NAME)
        if (f) {
            throw IllegalArgumentException("Fights already contain competitors, brackets are generated.") //add capability to re-generate the competitors
        }
        //check if the actual 1st numbers match (sometimes it is 0 sometimes it is 1)
        val absFights = mongoTemplate.find(Query(Criteria(CATEGORY_ID).`is`(categoryId).and(COMPETITION_ID).`is`(competitionId)), FightDescription::class.java, COLLECTION_NAME)
        val minRound = absFights.map { it.round ?: -1 }.min() ?: -1
        val minRoundIncoming = fights.map { it.round ?: -1 }.min() ?: -1
        if (minRound < 0 || minRoundIncoming < 0) {
            throw Exception("Something happened, not all fights contain round")
        }
        val delta = minRound - minRoundIncoming
        if (!fights.map {
                    val newId = incrementRoundId(delta, it.fightId)
                    mongoTemplate.count(Query(Criteria(FIGHT_ID).`is`(newId).and(COMPETITION_ID).`is`(competitionId)),
                            FightDescription::class.java, COLLECTION_NAME) > 0
                }.reduce { a, b -> a && b }) {
            throw Exception("Something happened, not all fights that should be updated exist in the DB.")
        }
        fights.forEach {
            val newId = incrementRoundId(delta, it.fightId)
            var upd = Update().push(COMPETITORS).each(it.competitors)
            if (!it.winFight.isNullOrBlank()) {
                upd = upd.set(WIN_FIGHT, incrementRoundId(delta, it.winFight!!))
            }
            if (!it.loseFight.isNullOrBlank()) {
                upd = upd.set(LOSE_FIGHT, incrementRoundId(delta, it.loseFight!!))
            }
            mongoTemplate.updateFirst(Query(Criteria(FIGHT_ID).`is`(newId)), upd, FightDescription::class.java, COLLECTION_NAME)
        }

    }

    fun getAll(competitionId: String?): List<FightDescription>? = competitionId?.let {
        val criteria = Criteria(COMPETITION_ID).`is`(competitionId)
        mongoTemplate.find(Query(criteria), FightDescription::class.java, COLLECTION_NAME)
    } ?: mongoTemplate.findAll(FightDescription::class.java, COLLECTION_NAME)

    fun findByCompetitionId(competitionId: String): List<FightDescription> {
        val criteria = Criteria(COMPETITION_ID).`is`(competitionId)
        return mongoTemplate.find(Query(criteria), FightDescription::class.java, COLLECTION_NAME) ?: emptyList()
    }

    fun findByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): List<FightDescription> {
        val criteria = Criteria(COMPETITION_ID).`is`(competitionId).and(CATEGORY_ID).`is`(categoryId)
        return mongoTemplate.find(Query(criteria), FightDescription::class.java, COLLECTION_NAME) ?: emptyList()
    }

    fun findByCategoryIdAndCompetitionId(categoryId: String, competitionId: String): List<FightDescription>? {
        val criteria = Criteria(COMPETITION_ID).`is`(competitionId).and(CATEGORY_ID).`is`(categoryId)
        return mongoTemplate.find(Query(criteria), FightDescription::class.java, COLLECTION_NAME)
    }

    fun deleteAll(competitionId: String) {
        mongoTemplate.remove(Query(Criteria(COMPETITION_ID).`is`(competitionId)), FightDescription::class.java, COLLECTION_NAME)
    }

    fun update(fight: FightDescription) {
        mongoTemplate.save(fight, COLLECTION_NAME)
    }

    fun updateScores(event: ScoreUpdateEvent, competitionId: String) {
        val query = Query(Criteria(FIGHT_ID).`is`(event.fightId).and(COMPETITION_ID).`is`(competitionId).and(SCORES).elemMatch(Criteria(COMPETITOR_ID).`is`(event.scores.competitorId)))
        var update = Update()
        event.scores.points.let { update = update.set("$SCORES.\$.$POINTS", it) }
        event.scores.advantages.let { update = update.set("$SCORES.\$.$ADVANTAGES", it) }
        event.scores.penalties.let { update = update.set("$SCORES.\$.$PENALTIES", it) }
        mongoTemplate.updateFirst(query, update, FightDescription::class.java, COLLECTION_NAME)
    }

    fun udateFightCompetitionId(fightId: String, updatedCompId: String, competitionId: String) {
        val query = Query(Criteria(FIGHT_ID).`is`(fightId).and(COMPETITION_ID).`is`(competitionId))
        val update = Update().set(COMPETITION_ID, updatedCompId)
        mongoTemplate.updateFirst(query, update, FightDescription::class.java, COLLECTION_NAME)
    }

    fun updateFightStage(event: FightStageUpdateEvent, competitionId: String) {
        mongoTemplate.findAndModify(Query(Criteria(FIGHT_ID).`is`(event.fightId).and(COMPETITION_ID).`is`(competitionId).and(STAGE).ne(FINISHED)), Update().set(STAGE, event.fightStage), FightDescription::class.java, COLLECTION_NAME)
    }

    fun findWithIds(ids: List<String>, competitionId: String): List<FightDescription>? = mongoTemplate.find(Query(Criteria(COMPETITION_ID).`is`(competitionId).and(FIGHT_ID).`in`(ids)), FightDescription::class.java, COLLECTION_NAME)

}