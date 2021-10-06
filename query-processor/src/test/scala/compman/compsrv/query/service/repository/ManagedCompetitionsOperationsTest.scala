package compman.compsrv.query.service.repository

import com.typesafe.config.ConfigFactory
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.test.Assertion._
import zio.test._

object ManagedCompetitionsOperationsTest extends DefaultRunnableSpec with EmbeddedCassandra with TestEntities {
  type Env = RepoEnvironment
  private val config: CassandraContextConfig = CassandraContextConfig(ConfigFactory.load().getConfig("ctx"))
  private implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live
  private val layers = CompetitionLogging.Live.loggingLayer

  override def spec
  : ZSpec[Any, Throwable] = suite("managed competitions operations suite")(withCassandra { () =>
    testM("should save managed competition") {
      {
        val cassandraZioSession = CassandraZioSession(config.cluster, config.keyspace, config.preparedStatementCacheSize)
        implicit val quillEnvironment: ManagedCompetitionsOperations.ManagedCompetitionService[LIO] =
          ManagedCompetitionsOperations.live(cassandraZioSession)
        for {
          _ <- ManagedCompetitionsOperations.addManagedCompetition[LIO](managedCompetition)
          competitions <- ManagedCompetitionsOperations.getManagedCompetitions[LIO]
          _ <- ManagedCompetitionsOperations.deleteManagedCompetition[LIO](managedCompetition.competitionId)
          shouldBeEmpty <- ManagedCompetitionsOperations.getManagedCompetitions[LIO]
        } yield assert(competitions)(isNonEmpty) && assert(shouldBeEmpty)(isEmpty)
      }.provideLayer(layers)
    }
  })
}
