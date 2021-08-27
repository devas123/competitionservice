package compman.compsrv.logic.service.competitor

import compman.compsrv.model.dto.competition.{AcademyDTO, CompetitorDTO}

import java.time.Instant
import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.Random

object CompetitorService {
  private val names = Array(
    "Vasya",
    "Kolya",
    "Petya",
    "Sasha",
    "Vanya",
    "Semen",
    "Grisha",
    "Kot",
    "Evgen",
    "Prohor",
    "Evgrat",
    "Stas",
    "Andrey",
    "Marina"
  )
  private val surnames = Array(
    "Vasin",
    "Kolin",
    "Petin",
    "Sashin",
    "Vanin",
    "Senin",
    "Grishin",
    "Kotov",
    "Evgenov",
    "Prohorov",
    "Evgratov",
    "Stasov",
    "Andreev",
    "Marinin"
  )
  private val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray

  private def generateRandomString(chars: Array[Char], random: Random, length: Int): String = {
    @tailrec
    def loop(result: StringBuilder, chars: Array[Char], length: Int, random: Random): String = {
      if (result.length >= length) { result.toString() }
      else { loop(result.append(chars(random.nextInt(chars.length))), chars, length, random) }
    }
    loop(new StringBuilder(), chars, length, random)
  }

  private def generateEmail(random: Random): String = {
    val emailBuilder = new StringBuilder()
    emailBuilder.append(generateRandomString(validChars, random, 10)).append("@")
      .append(generateRandomString(validChars, random, 7)).append(".")
      .append(generateRandomString(validChars, random, 4)).toString()
  }

  def generateRandomCompetitorsForCategory(
    size: Int,
    academies: Int = 20,
    categoryId: String,
    competitionId: String
  ): List[CompetitorDTO] = {
    val random = new Random()
    val result = ListBuffer.empty[CompetitorDTO]
    for (_ <- 1 to size) {
      val email = generateEmail(random)
      result.addOne(
        new CompetitorDTO().setId(UUID.randomUUID().toString).setEmail(email)
          .setFirstName(names(random.nextInt(names.length))).setLastName(surnames(random.nextInt(surnames.length)))
          .setBirthDate(Instant.now()).setRegistrationStatus("SUCCESS_CONFIRMED")
          .setAcademy(new AcademyDTO(UUID.randomUUID().toString, s"Academy${random.nextInt(academies)}"))
          .setCategories(Array(categoryId)).setCompetitionId(competitionId)
      )
    }
    result.toList
  }
}
