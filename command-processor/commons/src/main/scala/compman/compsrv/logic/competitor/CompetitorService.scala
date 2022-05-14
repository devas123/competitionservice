package compman.compsrv.logic.competitor

import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compservice.model.protobuf.model.{Academy, Competitor, CompetitorRegistrationStatus}

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable
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
    def loop(result: mutable.StringBuilder, chars: Array[Char], length: Int, random: Random): String = {
      if (result.length >= length) { result.toString() }
      else { loop(result.append(chars(random.nextInt(chars.length))), chars, length, random) }
    }
    loop(new mutable.StringBuilder(), chars, length, random)
  }

  private def generateEmail(random: Random): String = {
    val emailBuilder = new mutable.StringBuilder()
    emailBuilder.append(generateRandomString(validChars, random, 10)).append("@")
      .append(generateRandomString(validChars, random, 7)).append(".")
      .append(generateRandomString(validChars, random, 4)).toString()
  }

  def generateRandomCompetitorsForCategory(
    size: Int,
    academies: Int = 20,
    categoryId: String,
    competitionId: String
  ): List[Competitor] = {
    val random = new Random()
    val result = ListBuffer.empty[Competitor]
    for (_ <- 1 to size) {
      val email = generateEmail(random)
      result.addOne(
        Competitor()
          .withId(UUID.randomUUID().toString)
          .withEmail(email)
          .withFirstName(names(random.nextInt(names.length)))
          .withLastName(surnames(random.nextInt(surnames.length)))
          .withBirthDate(Timestamp.fromJavaProto(Timestamps.fromMillis(System.currentTimeMillis())))
          .withRegistrationStatus(CompetitorRegistrationStatus.SUCCESS_CONFIRMED)
          .withAcademy(Academy(UUID.randomUUID().toString, s"Academy${random.nextInt(academies)}"))
          .withCategories(Array(categoryId))
          .withCompetitionId(competitionId)
      )
    }
    result.toList
  }
}
