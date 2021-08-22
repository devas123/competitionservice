package compman.compsrv.logic.service

import cats.data.OptionT
import cats.implicits._
import cats.{Monad, Traverse}
import compman.compsrv.logic.service.CompetitorSelection.{firstNPlaces, lastNPlaces, returnIds}
import compman.compsrv.model.CompetitionState
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition._
import zio.Task
import zio.interop.catz._

import java.time.Instant
import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.Random

trait FightsService[F[+_]] {
  def applyStageInputDescriptorToResultsAndFights(
      descriptor: StageInputDescriptorDTO,
      previousStageId: String,
      state: CompetitionState
  ): F[List[String]]

  def supportedBracketTypes(): F[List[BracketType]]

  def generateStageFights(
      competitionId: String,
      categoryId: String,
      stage: StageDescriptorDTO,
      compssize: Int,
      duration: BigDecimal,
      competitors: List[CompetitorDTO],
      outputSize: Int
  ): F[List[FightDescriptionDTO]]

  def distributeCompetitors(
      competitors: List[CompetitorDTO],
      fights: List[FightDescriptionDTO],
      bracketType: BracketType
  ): F[List[FightDescriptionDTO]]
  def buildStageResults(
      bracketType: BracketType,
      stageStatus: StageStatus,
      stageType: StageType,
      fights: List[FightDescriptionDTO],
      stageId: String,
      competitionId: String,
      fightResultOptions: List[FightResultOptionDTO]
  ): F[List[CompetitorStageResultDTO]]
}

object FightsService {
  val live: FightsService[Task] =
    new FightsService[Task] {
      override def applyStageInputDescriptorToResultsAndFights(
          descriptor: StageInputDescriptorDTO,
          previousStageId: String,
          state: CompetitionState
      ): Task[List[String]] = {
        val program =
          if (Option(descriptor.getSelectors).exists(_.nonEmpty)) {
            descriptor
              .getSelectors
              .flatMap(it => {
                val classifier = it.getClassifier
                classifier match {
                  case SelectorClassifier.FIRST_N_PLACES =>
                    List(firstNPlaces(it.getApplyToStageId, it.getSelectorValue.head.toInt))
                  case SelectorClassifier.LAST_N_PLACES =>
                    List(lastNPlaces(it.getApplyToStageId, it.getSelectorValue.head.toInt))
                  case SelectorClassifier.MANUAL =>
                    List(returnIds(it.getSelectorValue.toList))
                }
              })
              .reduce((a, b) => CompetitorSelection.and(a, b))
          } else {
            firstNPlaces(previousStageId, descriptor.getNumberOfCompetitors)
          }
        program.foldMap(CompetitorSelection.asTask(state)).map(_.toList)
      }

      override def supportedBracketTypes(): Task[List[BracketType]] = ???

      override def generateStageFights(
          competitionId: String,
          categoryId: String,
          stage: StageDescriptorDTO,
          compssize: Int,
          duration: BigDecimal,
          competitors: List[CompetitorDTO],
          outputSize: Int
      ): Task[List[FightDescriptionDTO]] = ???

      override def distributeCompetitors(
          competitors: List[CompetitorDTO],
          fights: List[FightDescriptionDTO],
          bracketType: BracketType
      ): Task[List[FightDescriptionDTO]] = ???

      override def buildStageResults(
          bracketType: BracketType,
          stageStatus: StageStatus,
          stageType: StageType,
          fights: List[FightDescriptionDTO],
          stageId: String,
          competitionId: String,
          fightResultOptions: List[FightResultOptionDTO]
      ): Task[List[CompetitorStageResultDTO]] = ???
    }
  val finishedStatuses = List(FightStatus.UNCOMPLETABLE, FightStatus.FINISHED, FightStatus.WALKOVER)
  val unMovableFightStatuses: Seq[FightStatus] = finishedStatuses :+ FightStatus.IN_PROGRESS
  val notFinishedStatuses = List(
    FightStatus.PENDING,
    FightStatus.IN_PROGRESS,
    FightStatus.GET_READY,
    FightStatus.PAUSED
  )
  val SEMI_FINAL        = "Semi-final"
  val QUARTER_FINAL     = "Quarter-final"
  val FINAL             = "Final"
  val WINNER_FINAL      = "Winner-final"
  val GRAND_FINAL       = "Grand final"
  val ELIMINATION       = "Elimination"
  val THIRD_PLACE_FIGHT = "Third place"
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
  private val validChars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray

  def getWinnerId(fight: FightDescriptionDTO): Option[String] = Option(fight.getFightResult)
    .map(_.getWinnerId)
  def getLoserId(fight: FightDescriptionDTO): Option[String] =
    for {
      r      <- Option(fight.getFightResult)
      wid    <- Option(r.getWinnerId)
      scores <- Option(fight.getScores)
      loser  <- scores.find(_.getCompetitorId != wid)
      id     <- Option(loser.getCompetitorId)
    } yield id

  def moveFighterToSiblings[F[_]: Monad](
      competitorId: String,
      fromFightId: String,
      referenceType: FightReferenceType,
      fights: Map[String, FightDescriptionDTO]
  ): F[List[FightDescriptionDTO]] = {
    def getTargetFightId(fight: FightDescriptionDTO) = {
      if (referenceType == FightReferenceType.LOSER) {
        Option(fight.getLoseFight)
      } else {
        Option(fight.getWinFight)
      }
    }
    val opt: OptionT[F, List[FightDescriptionDTO]] =
      for {
        fight <- OptionT.fromOption[F](fights.get(fromFightId))
        tfid <- OptionT.fromOption[F](
          getTargetFightId(fight).orElse(
            fights
              .values
              .find(fg =>
                fg.getScores
                  .exists(s => {
                    s.getParentFightId == fromFightId && s.getParentReferenceType == referenceType
                  })
              )
              .map(_.getId)
          )
        )
        updatedFights = fights.mapFilter(it => {
          if (it.getId == tfid) {
            val newScores = it
              .getScores
              .map(score => {
                if (
                  score.getParentFightId == fromFightId &&
                  score.getParentReferenceType == referenceType
                ) {
                  score.setCompetitorId(competitorId)
                } else {
                  score
                }
              })
            Some(it.setScores(newScores))
          } else {
            None
          }
        })
        uncompletableFight = updatedFights.values.find(_.getStatus == FightStatus.UNCOMPLETABLE)
        res <-
          if (uncompletableFight.isDefined) {
            OptionT.liftF[F, List[FightDescriptionDTO]](
              moveFighterToSiblings[F](
                competitorId,
                uncompletableFight.get.getId,
                FightReferenceType.WINNER,
                fights
              ).map(_ ++ updatedFights.values).map(_.toList)
            )
          } else {
            OptionT.fromOption[F](Option(updatedFights.values.toList))
          }
      } yield res
    opt.value.map(_.getOrElse(List.empty))
  }

  def markAndProcessUncompletableFights[F[_]: Monad: CompetitionStateOperations](
      fights: Map[String, FightDescriptionDTO],
  ): F[List[FightDescriptionDTO]] = {

    def updateFightResult(f: FightDescriptionDTO) = Option(f.getFightResult)
      .orElse(
        f.getScores
          .find(_.getCompetitorId != null)
          .map(s =>
            new FightResultDTO(s.getCompetitorId, FightResultOptionDTO.WALKOVER.getId, "BYE")
          )
      )
      .map(f.setFightResult)
      .getOrElse(f)
    for {
      updatedFights <-
        for {
          options <-
            Traverse[List].traverse(fights.values.toList)(it =>
              for {
                fightPacked <- checkIfFightIsPackedOrCanBePackedEventually(it.getId)
                res =
                  if (fightPacked)
                    None
                  else
                    Some(updateFightResult(it).setStatus(FightStatus.UNCOMPLETABLE))
              } yield res
            )
        } yield options.mapFilter(identity)
      r <-
        updatedFights
          .flatMap(f => f.getScores.map(s => (s.getCompetitorId, f.getId)))
          .foldLeftM(fights ++ updatedFights.map(f => (f.getId, f)).toMap)((acc, t) =>
            for {
              uf <- moveFighterToSiblings(t._1, t._2, FightReferenceType.WINNER, acc)(Monad[F])
            } yield acc ++ uf.map(f => (f.getId, f)).toMap
          )
    } yield r.values.toList
  }

  def ceilingNextPowerOfTwo(x: Int): Int = 1 << (32 - Integer.numberOfLeadingZeros(x - 1))

  def filterPreliminaryFights[F[_]: Monad](
      outputSize: Int,
      fights: List[FightDescriptionDTO],
      bracketType: BracketType
  ): F[List[FightDescriptionDTO]] = {
    val result =
      bracketType match {
        case BracketType.SINGLE_ELIMINATION =>
          val ceiling = ceilingNextPowerOfTwo(outputSize)
          val roundsToReturn =
            fights
              .groupBy(_.getRound)
              .map(entry => {
                entry._1 -> entry._2.size
              })
              .filter(it => {
                it._2 * 2 > ceiling
              })
              .keys
              .toSet
          fights.filter(it => {
            roundsToReturn.contains(it.getRound)
          })
        case BracketType.GROUP =>
          fights
        case _ =>
          ???
      }
    for {
      a <-
        update[F](result)(
          it => {
            it.getWinFight != null &&
            result.exists(r => {
              r.getId == it.getWinFight
            })
          },
          it => {
            it.setWinFight(null)
          }
        )
      b <-
        update[F](a)(
          it => {
            it.getWinFight != null &&
            result.exists(r => {
              r.getId == it.getLoseFight
            })
          },
          it => {
            it.setLoseFight(null)
          }
        )
    } yield b
  }

  def update[F[_]: Monad](coll: List[FightDescriptionDTO])(
      condition: FightDescriptionDTO => Boolean,
      update: FightDescriptionDTO => FightDescriptionDTO
  ): F[List[FightDescriptionDTO]] =
    Traverse[List].traverse(coll)(f =>
      if (condition(f))
        Monad[F].pure(update(f))
      else
        Monad[F].pure(f)
    )
  private def generateRandomString(chars: Array[Char], random: Random, length: Int): String = {
    @tailrec
    def loop(result: StringBuilder, chars: Array[Char], length: Int, random: Random): String = {
      if (result.length >= length) {
        result.toString()
      } else {
        loop(result.append(chars(random.nextInt(chars.length))), chars, length, random)
      }
    }
    loop(new StringBuilder(), chars, length, random)
  }

  private def generateEmail(random: Random): String = {
    val emailBuilder = new StringBuilder()
    emailBuilder
      .append(generateRandomString(validChars, random, 10))
      .append("@")
      .append(generateRandomString(validChars, random, 7))
      .append(".")
      .append(generateRandomString(validChars, random, 4))
      .toString()
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
        new CompetitorDTO()
          .setId(UUID.randomUUID().toString)
          .setEmail(email)
          .setFirstName(names(random.nextInt(names.length)))
          .setLastName(surnames(random.nextInt(surnames.length)))
          .setBirthDate(Instant.now())
          .setRegistrationStatus("SUCCESS_CONFIRMED")
          .setAcademy(
            new AcademyDTO(UUID.randomUUID().toString, s"Academy${random.nextInt(academies)}")
          )
          .setCategories(Array(categoryId))
          .setCompetitionId(competitionId)
      )
    }
    result.toList
  }

  def generatePlaceholderCompetitorsForGroup[F[_]: Monad](size: Int): F[List[CompetitorDTO]] = {
    Monad[F].pure(
      (0 until size)
        .map(it => new CompetitorDTO().setId(s"placeholder-$it").setPlaceholder(true))
        .toList
    )
  }

  private def checkIfFightCanProduceReference[F[_]: CompetitionStateOperations: Monad](
      fightId: String,
      referenceType: FightReferenceType
  ): F[Boolean] = {
    def atLeastOneParentCanProduce(parentFights: List[(FightReferenceType, String)]) = {
      Traverse[List]
        .traverse(parentFights)(pf => checkIfFightCanProduceReference(pf._2, pf._1))
        .map(_.exists(b => b))
    }

    for {
      fightScores <- CompetitionStateOperations[F].getFightScores(fightId)
      parentFights = fightScores.mapFilter(fs =>
        Option(fs.getParentReferenceType).flatMap(rt =>
          if (fs.getParentFightId != null)
            Option((rt, fs.getParentFightId))
          else
            None
        )
      )
      res <-
        if (!fightScores.exists(_.getCompetitorId != null)) {
          referenceType match {
            case FightReferenceType.WINNER =>
              atLeastOneParentCanProduce(parentFights)
            case _ =>
              Traverse[List]
                .traverse(parentFights)(pf => checkIfFightCanProduceReference(pf._2, pf._1))
                .map(_.count(b => b) == 2)
          }

        } else if (fightScores.count(_.getCompetitorId != null) == 1) {
          referenceType match {
            case FightReferenceType.WINNER =>
              Monad[F].pure(true)
            case _ =>
              atLeastOneParentCanProduce(parentFights)
          }
        } else {
          Monad[F].pure(true)
        }
    } yield res
  }

  trait CompetitionStateOperations[F[_]] {
    def getFightScores(id: String): F[List[CompScoreDTO]]
  }
  object CompetitionStateOperations {
    def apply[F[_]](implicit F: CompetitionStateOperations[F]): CompetitionStateOperations[F] = F
  }

  private def checkIfFightIsPackedOrCanBePackedEventually[F[_]: CompetitionStateOperations: Monad](
      fightId: String
  ): F[Boolean] = {
    for {
      scores <- CompetitionStateOperations[F].getFightScores(fightId)
      list <-
        Traverse[List].traverse(scores)(sc => {
          if (sc.getCompetitorId != null) {
            Monad[F].pure(true)
          } else if (sc.getParentFightId != null && sc.getParentReferenceType != null) {
            checkIfFightCanProduceReference(sc.getParentFightId, sc.getParentReferenceType)
          } else {
            Monad[F].pure(false)
          }
        })
    } yield list.count(a => a) == 2
  }

  def createCompscore(competitorId: String, placeholderId: String, order: Int, parentReferenceType: FightReferenceType = FightReferenceType.PROPAGATED): CompScoreDTO = new CompScoreDTO()
    .setCompetitorId(competitorId)
    .setScore(generate.createEmptyScore)
    .setPlaceholderId(placeholderId)
    .setOrder(order)
    .setParentReferenceType(parentReferenceType)

}
