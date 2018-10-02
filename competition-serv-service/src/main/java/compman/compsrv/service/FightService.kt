package compman.compsrv.service

import compman.compsrv.client.AccountServiceClient
import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.competition.FightDescription
import compman.compsrv.model.competition.GenerateAbsoluteMessage
import compman.compsrv.repository.FightRepository
import compman.compsrv.ws.FightServiceProvider
import org.springframework.stereotype.Component
import java.util.*
import kotlin.collections.ArrayList

@Component
class FightService constructor(private val fightRepository: FightRepository,
                               private val participantsService: AccountServiceClient,
                               private val generateService: FightsGenerateService) : FightServiceProvider {
    override fun generateAbsoluteCategory(message: GenerateAbsoluteMessage): List<FightDescription>? {
        val arrc = ArrayList<Competitor>().apply { addAll(message.competitors) }
        return generateService.generateRoundsForCategory(message.category, arrc.shuffle(), message.competitionId).map { it.copy(competitionId = message.competitionId) }
    }

    override fun updateFight(fight: FightDescription) {
        fightRepository.update(fight)
    }

    override fun deleteFightsForCategory(competitionId: String, categoryId: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteAllFights(competitionId: String) {
        fightRepository.deleteAll(competitionId)
    }


    override fun generateFights(competitionId: String): List<FightDescription>? = generateService.generatePlayOff(participantsService.getConfirmedCompetitors(competitionId), competitionId)

    override fun generateFightsForCategory(competitionId: String, category: Category): List<FightDescription>? {
        return participantsService.getConfirmedCompetitorsByCategory(competitionId, category.categoryId!!).let { generateService.generateRoundsForCategory(category, it.toCollection(ArrayList()).shuffle(), competitionId) }
    }


    override fun saveFights(fights: List<FightDescription>) {
        fightRepository.save(fights.toTypedArray())
    }


    override fun getAllFights(competitionId: String): List<FightDescription> {
        return fightRepository.getAll(competitionId) ?: emptyList()
    }

    override fun getFightsForCategory(competitionId: String, categoryId: String): List<FightDescription>? = fightRepository.findByCategoryIdAndCompetitionId(categoryId, competitionId)


    //extensions

    private fun ArrayList<Competitor>.shuffle(): ArrayList<Competitor> {
        val rg = Random()
        for (i in 0 until this.size) {
            val randomPosition = rg.nextInt(this.size)
            val tmp: Competitor = this[i]
            this[i] = this[randomPosition]
            this[randomPosition] = tmp
        }
        return this
    }
}
