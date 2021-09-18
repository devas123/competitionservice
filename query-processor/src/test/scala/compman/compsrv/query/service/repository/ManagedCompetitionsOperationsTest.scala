package compman.compsrv.query.service.repository

import com.typesafe.config.ConfigFactory
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.Service
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZLayer

import java.time.Instant

object ManagedCompetitionsOperationsTest extends DefaultRunnableSpec {
  type Env = RepoEnvironment
  private val cassandraEnvironment = CassandraZioSession
    .fromContextConfig(CassandraContextConfig(ConfigFactory.load().getConfig("ctx")))
  private val quillEnvironment = ManagedCompetitionsOperations.live(CompetitionLogging.Live.live)
  val layers: ZLayer[Any, Throwable, Service[Env] with Env] = quillEnvironment ++ cassandraEnvironment ++
    CompetitionLogging.Live.loggingLayer
  override def spec
    : ZSpec[Any, Throwable] = suite("managed competitions operations suite")(testM("should save managed competition") {
    val managedCompetition = ManagedCompetition(
      "competitionId",
      "ecompetition-id-topic",
      "valera_protas",
      Instant.now(),
      Instant.now(),
      Instant.now(),
      "UTC",
      CompetitionStatus.CREATED
    )
    (for {
      _             <- ManagedCompetitionsOperations.addManagedCompetition[Env](managedCompetition)
      competitions  <- ManagedCompetitionsOperations.getManagedCompetitions[Env]
      _             <- ManagedCompetitionsOperations.deleteManagedCompetition[Env](managedCompetition.competitionId)
      shouldBeEmpty <- ManagedCompetitionsOperations.getManagedCompetitions[Env]
    } yield assert(competitions)(isNonEmpty) && assert(shouldBeEmpty)(isEmpty)).provideLayer(layers)
  }) @@ sequential
}
