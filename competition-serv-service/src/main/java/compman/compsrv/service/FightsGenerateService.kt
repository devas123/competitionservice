package compman.compsrv.service

import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.jpa.competition.Academy
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.exceptions.CategoryNotFoundException
import compman.compsrv.repository.CategoryDescriptorCrudRepository
import compman.compsrv.util.IDGenerator
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class FightsGenerateService(private val categoryCrudRepository: CategoryDescriptorCrudRepository) {

    companion object {
        private val names = arrayOf("Vasya", "Kolya", "Petya", "Sasha", "Vanya", "Semen", "Grisha", "Kot", "Evgen", "Prohor", "Evgrat", "Stas", "Andrey", "Marina")
        private val surnames = arrayOf("Vasin", "Kolin", "Petin", "Sashin", "Vanin", "Senin", "Grishin", "Kotov", "Evgenov", "Prohorov", "Evgratov", "Stasov", "Andreev", "Marinin")
        private val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray()

        private fun generateRandomString(chars: CharArray, random: Random, length: Int): String {
            val builder = StringBuilder()
            for (i in 0..length) {
                builder.append(chars[random.nextInt(chars.size)])
            }
            return builder.toString()
        }

        private fun generateEmail(random: Random): String {
            val emailBuilder = StringBuilder()
            return emailBuilder
                    .append(generateRandomString(validChars, random, 10)).append("@")
                    .append(generateRandomString(validChars, random, 7)).append(".")
                    .append(generateRandomString(validChars, random, 4)).toString()
        }

        fun generateRandomCompetitorsForCategory(size: Int, academies: Int = 20, category: CategoryDescriptor, competitionId: String): List<Competitor> {
            val random = Random()
            val result = ArrayList<Competitor>()
            for (k in 1 until size + 1) {
                val email = generateEmail(random)
                result.add(Competitor(
                        IDGenerator.hashString("$competitionId/${category.id}/$email"),
                        email,
                        null,
                        names[random.nextInt(names.size)],
                        surnames[random.nextInt(surnames.size)],
                        Instant.now(),
                        Academy("Academy${random.nextInt(academies)}", emptyList()).id,
                        category.id!!,
                        competitionId,
                        RegistrationStatus.UNKNOWN,
                        null))
            }
            return result
        }
    }

//    private fun createFakeCompetitors(size: Int, competitionId: String): List<Competitor> = Collections.nCopies(size,
//            Competitor(email = "fake", firstName = "fake", lastName = "fake",
//                    registrationStatus = RegistrationStatus.UNKNOWN, competitionId = competitionId,
//                    category = Category(
//                            ageDivision = AgeDivision("fake", Int.MIN_VALUE),
//                            competitionId = competitionId,
//                            categoryId = "fake",
//                            beltType = null,
//                            fightDuration = BigDecimal(Int.MAX_VALUE),
//                            gender = "FAKE",
//                            weight = null),
//                    promo = null, academy = null, birthDate = null, userId = null))

//    private fun getAbsCategoryMaxSize(absCategoryId: String, competitors: List<Competitor>?): Int {
//        return competitors
//                ?.filter { it.category.categoryId?.startsWith(absCategoryId) == true }
//                ?.groupBy { it.category.categoryId }
//                ?.map { (_, competitors: List<Competitor>) -> if (competitors.size >= 4) 4 else competitors.size }
//                ?.reduce { size1, size2 -> size1 + size2 } ?: 0
//    }

//    private fun createAbsoluteCategoryData(competitors: List<Competitor>?, competitionId: String): Map<Category, List<Competitor>> {
//        return competitors
//                ?.filter { it.category.beltType?.contains("WHITE", ignoreCase = true) == false }
//                ?.distinct()
//                ?.map { competitor ->
//                    val size = getAbsCategoryMaxSize(competitor.category.categoryId!!, competitors)
//                    val absCat = competitor.category.setCategoryId("${competitor.category.categoryId}/ABSOLUTE")
//                    if (size > 0) {
//                        absCat to createFakeCompetitors(size, competitionId)
//                    } else {
//                        absCat to emptyList()
//                    }
//                }?.toMap()?.filter { it.value.isNotEmpty() } ?: emptyMap()
//    }

    fun generatePlayOff(competitors: List<Competitor>?, competitionId: String): List<FightDescription> = ArrayList<FightDescription>()
            .apply {
                competitors
                        ?.filter { !it.categoryId.isBlank() }
                        ?.groupBy { it.categoryId }
                        ?.forEach { category, categoryCompetitors ->
                            this.addAll(generateRoundsForCategory(category, ArrayList<Competitor>().apply { addAll(categoryCompetitors) }, competitionId))
                        }
//        createAbsoluteCategoryData(competitors, competitionId).forEach { category, categoryCompetitors ->
//            addAll(generateRoundsForCategory(category, ArrayList<Competitor>().apply { addAll(categoryCompetitors.sortedBy { it.timestamp }) }, competitionId))
//        }
            }


    fun generateRoundsForCategory(categoryId: String, competitors: MutableList<Competitor>, competitionId: String) = ArrayList<FightDescription>().apply {
        if (competitors.size == 1) {
            add(FightDescription(
                    fightId = createFightId(competitionId, categoryId, 1, 0),
                    categoryId = categoryId,
                    round = 1,
                    winFight = null,
                    competitionId = competitionId,
                    numberInRound = 0)
                    .setDuration(calculateDuration(categoryId))
                    .pushCompetitor(competitors.removeAt(0)))
        } else {
            var qualifiyngCount = 0
            nearestPowTwo(competitors.size).let { clearCompetitorsSize ->
                (Math.log(clearCompetitorsSize.toDouble()) / Math.log(2.0)).toInt().let { rounds ->
                    if ((competitors.size and (competitors.size - 1)) != 0) {
                        qualifiyngCount = competitors.size - clearCompetitorsSize
                        addAll(fightsForRound(0, categoryId, clearCompetitorsSize, 0, competitors, competitionId)) //if need only hard counted fights in round 0  then instead of clearCompetitorsSize need to pass competitors.size-clearCompetitorsSize
                    }
                    var size = clearCompetitorsSize / 2
                    repeat(rounds + 1) {
                        if (it != 0) {
                            addAll(fightsForRound(it, categoryId, size, if (it == 1) qualifiyngCount else 0, competitors, competitionId))
                            size /= 2
                        }
                    }
                }
            }
        }
    }

    private fun createFightId(competitionId: String, categoryId: String?, rount: Int, number: Int) = "$competitionId-$categoryId-$rount-$number"

    private fun fightsForRound(round: Int, categoryId: String, fightsSize: Int, qualifyingCount: Int = 0, competitors: MutableList<Competitor>, competitionId: String) = ArrayList<FightDescription>().apply {

        var nextCounter = 0
        var nextFightNumber = 0
        val competitorsSize = competitors.size
        fun createOddFightId(it: Int) =
                if (round != 1 || (it * 2) < qualifyingCount) createFightId(competitionId, categoryId, round - 1, it * 2) else null

        fun createEvenFightId(it: Int) =
                if (round != 1 || (it * 2) + 1 < qualifyingCount) createFightId(competitionId, categoryId, round - 1, (it * 2) + 1) else null

        repeat(fightsSize) { index ->
            if (nextCounter > 1) {
                nextCounter = 0; nextFightNumber++
            }
            var fight = FightDescription(
                    fightId = createFightId(competitionId, categoryId, round, index),
                    categoryId = categoryId,
                    round = round,
                    numberInRound = index,
                    winFight = createFightId(competitionId, categoryId, round + 1, nextFightNumber),
                    competitionId = competitionId).setDuration(calculateDuration(categoryId))
            if (round != 0) {
                createOddFightId(index)?.let {
                    fight = fight.copy(parentId1 = it)
                } ?: if (round == 1 && !competitors.isEmpty()) {
                    fight = fight.pushCompetitor(competitors.removeAt(0))
                }
                createEvenFightId(index)?.let {
                    fight = fight.copy(parentId2 = it)
                } ?: if (round == 1 && !competitors.isEmpty()) {
                    fight = fight.pushCompetitor(competitors.removeAt(0))
                }
            } else if (size < competitorsSize - fightsSize) {
                repeat(2) {
                    if (!competitors.isEmpty()) {
                        fight = fight.pushCompetitor(competitors.removeAt(0))
                    }
                }
                if (index == 0 && competitorsSize == 3 && round == 0) {
                    fight = fight.copy(loseFight = createFightId(competitionId, categoryId, round, index + 1))
                }
            } else if (index == 1 && competitorsSize == 3) {
                fight = fight.pushCompetitor(competitors.removeAt(0))
            }

            add(fight)
            nextCounter++
        }
    }


    private fun calculateDuration(categoryId: String) = categoryCrudRepository.findById(categoryId).map { it.fightDuration.toLong() }.orElseThrow { CategoryNotFoundException("Category not found for id: $categoryId") }

    private fun nearestPowTwo(number: Int): Int {
        if ((number and -number) == number) {
            return number
        }
        var result = 0
        for (i in 1..100) {
            if (number - Math.pow(2.0, i.toDouble()).toInt() <= 0) {
                return result
            }
            result = Math.pow(2.0, i.toDouble()).toInt()
        }
        return result
    }

}