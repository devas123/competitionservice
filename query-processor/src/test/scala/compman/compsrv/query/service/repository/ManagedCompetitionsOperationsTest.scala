package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.{URIO, ZIO}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.aroundAll

object ManagedCompetitionsOperationsTest extends DefaultRunnableSpec with EmbeddedMongoDb with TestEntities {
  type Env = RepoEnvironment
  private val layers = CompetitionLogging.Live.loggingLayer

  override def spec
    : ZSpec[Any, Throwable] = suite("managed competitions operations suite")(testM("should save managed competition") {
    {
      val context = EmbeddedMongoDb.context
      import context._
      for {
        _             <- ManagedCompetitionsOperations.addManagedCompetition[LIO](managedCompetition)
        competitions  <- ManagedCompetitionsOperations.getActiveCompetitions[LIO]
        _             <- ManagedCompetitionsOperations.deleteManagedCompetition[LIO](managedCompetition.id)
        shouldBeEmpty <- ManagedCompetitionsOperations.getActiveCompetitions[LIO]
      } yield assert(competitions)(isNonEmpty) && assert(shouldBeEmpty)(isEmpty)
    }.provideLayer(layers)
  }) @@ aroundAll(ZIO.effect(startEmbeddedMongo()))(server => URIO(server._1.stop()))
}
