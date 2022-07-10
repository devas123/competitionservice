package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import org.testcontainers.containers.MongoDBContainer
import zio.ZManaged
import zio.test._

object ManagedCompetitionsOperationsTest extends DefaultRunnableSpec with EmbeddedMongoDb with TestEntities {
  type Env = RepoEnvironment
  private val layers = Compman.compsrv.interop.loggingLayer
  val mongoLayer: ZManaged[Any, Nothing, MongoDBContainer] = embeddedMongo()

  override def spec
  : ZSpec[Any, Throwable] = suite("managed competitions operations suite")(testM("should save managed competition") {
    {
      mongoLayer.use { mongo =>
        val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
        import context._
        for {
          _ <- ManagedCompetitionsOperations.addManagedCompetition[LIO](managedCompetition)
          competitions <- ManagedCompetitionsOperations.getActiveCompetitions[LIO]
          _ <- ManagedCompetitionsOperations.deleteManagedCompetition[LIO](managedCompetition.id)
          shouldBeEmpty <- ManagedCompetitionsOperations.getActiveCompetitions[LIO]
        } yield assertTrue(competitions.nonEmpty) && assertTrue(shouldBeEmpty.isEmpty)
      }.provideLayer(layers)
    }
  })
}
