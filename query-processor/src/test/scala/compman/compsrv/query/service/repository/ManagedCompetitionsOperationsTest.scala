package compman.compsrv.query.service.repository

import com.github.nosan.embedded.cassandra.api.Cassandra
import com.typesafe.config.ConfigFactory
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.model.ManagedCompetition
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.ZLayer
import zio.logging.Logging

import java.time.Instant

object ManagedCompetitionsOperationsTest extends DefaultRunnableSpec with EmbeddedCassandra with TestEntities {
  type Env = RepoEnvironment
  private val config: CassandraContextConfig = CassandraContextConfig(ConfigFactory.load().getConfig("ctx"))
  private val cassandraZioSession =
    CassandraZioSession(config.cluster, config.keyspace, config.preparedStatementCacheSize)
  private implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live
  private implicit val quillEnvironment: ManagedCompetitionsOperations.ManagedCompetitionService[LIO] =
    ManagedCompetitionsOperations.live(cassandraZioSession)
  val layers: ZLayer[Any, Throwable, Logging] = CompetitionLogging.Live.loggingLayer
  var cassandra: Option[Cassandra] = None

  def beforeAll(): Unit = {
    cassandra = Some(startEmbeddedCassandra())
  }
  def afterAll(): Unit = {
    cassandra.foreach(_.stop())
  }

  override def spec
    : ZSpec[Any, Throwable] = suite("managed competitions operations suite")(testM("should save managed competition") {
    (for {

      _             <- ManagedCompetitionsOperations.addManagedCompetition[LIO](managedCompetition)
      competitions  <- ManagedCompetitionsOperations.getManagedCompetitions[LIO]
      _             <- ManagedCompetitionsOperations.deleteManagedCompetition[LIO](managedCompetition.competitionId)
      shouldBeEmpty <- ManagedCompetitionsOperations.getManagedCompetitions[LIO]
    } yield assert(competitions)(isNonEmpty) && assert(shouldBeEmpty)(isEmpty)).provideLayer(layers)
  }) @@ sequential
}
