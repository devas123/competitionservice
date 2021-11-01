package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.test._
import zio.test.Assertion._

object ManagedCompetitionsOperationsTest extends DefaultRunnableSpec with EmbeddedMongoDb with TestEntities {
  type Env = RepoEnvironment
  private val layers    = CompetitionLogging.Live.loggingLayer
  private val cassandra = getCassandraResource

  import EmbeddedMongoDb._
  override def spec
    : ZSpec[Any, Throwable] = suite("managed competitions operations suite")(testM("should save managed competition") {
    {
      cassandra.use { _ =>
        implicit val quillEnvironment: ManagedCompetitionsOperations.ManagedCompetitionService[LIO] =
          ManagedCompetitionsOperations.live(mongoClient, mongodbConfig.queryDatabaseName)
        for {
          _             <- ManagedCompetitionsOperations.addManagedCompetition[LIO](managedCompetition)
          competitions  <- ManagedCompetitionsOperations.getActiveCompetitions[LIO]
          _             <- ManagedCompetitionsOperations.deleteManagedCompetition[LIO](managedCompetition.id)
          shouldBeEmpty <- ManagedCompetitionsOperations.getActiveCompetitions[LIO]
        } yield assert(competitions)(isNonEmpty) && assert(shouldBeEmpty)(isEmpty)
      }.provideLayer(layers)
    }
  })
}
