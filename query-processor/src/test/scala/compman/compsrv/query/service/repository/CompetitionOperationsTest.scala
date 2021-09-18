package compman.compsrv.query.service.repository

import com.typesafe.config.ConfigFactory
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.model.CompetitionProperties
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZLayer
import zio.logging.Logging

import java.time.Instant

object CompetitionOperationsTest extends DefaultRunnableSpec {
  type Env = QuillCassandraEnvironment with Logging
  private val cassandraEnvironment = CassandraZioSession
    .fromContextConfig(CassandraContextConfig(ConfigFactory.load().getConfig("ctx")))
  private implicit val queryOperations: CompetitionQueryOperations[RepoIO] = CompetitionQueryOperations
    .live(CompetitionLogging.Live.live)
  private implicit val updateOperations: CompetitionUpdateOperations[RepoIO] = CompetitionUpdateOperations
    .live(CompetitionLogging.Live.live)
  val layers: ZLayer[Any, Throwable, Env] = cassandraEnvironment ++ CompetitionLogging.Live.loggingLayer
  override def spec: ZSpec[Any, Throwable] = suite("competition operations")(
    testM("query should return none when there are no competitions") {
      (for {
        _ <- CompetitionUpdateOperations[RepoIO].removeCompetitionProperties("managedCompetition")
        props <- CompetitionQueryOperations.getCompetitionProperties("managedCompetition")
      } yield assert(props)(
        isNone
      )).provideLayer(layers)
    },
    testM("update should save competition") {
      val id = "managedCompetition"
      val competitionProperties = CompetitionProperties(
        id,
        "creatorId",
        Set("a", "b", "c"),
        "Some competition",
        CompetitionInfoTemplate("superlongdescriptionblob".getBytes),
        Instant.now(),
        schedulePublished = false,
        bracketsPublished = false,
        Instant.now(),
        "UTC",
        registrationOpen = true,
        Instant.now(),
        CompetitionStatus.CREATED
      )
      (for {
        _ <- CompetitionUpdateOperations[RepoIO].addCompetitionProperties(competitionProperties)
        props <- CompetitionQueryOperations.getCompetitionProperties(id)
      } yield assert(props)(isSome)).provideLayer(layers)
    }
  ) @@ sequential
}
