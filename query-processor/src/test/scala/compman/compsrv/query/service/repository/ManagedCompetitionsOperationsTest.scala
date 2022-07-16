package compman.compsrv.query.service.repository

import cats.effect.IO
import compman.compsrv.SpecBase
import compman.compsrv.logic.actors.behavior.WithIORuntime

import scala.util.Using

class ManagedCompetitionsOperationsTest extends SpecBase with EmbeddedMongoDb with TestEntities with WithIORuntime {

  test("Managed competitions operations test") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _             <- ManagedCompetitionsOperations.addManagedCompetition[IO](managedCompetition)
        competitions  <- ManagedCompetitionsOperations.getActiveCompetitions[IO]
        _             <- ManagedCompetitionsOperations.deleteManagedCompetition[IO](managedCompetition.id)
        shouldBeEmpty <- ManagedCompetitionsOperations.getActiveCompetitions[IO]
        _ <- IO {
          assert(competitions.nonEmpty)
          assert(shouldBeEmpty.isEmpty)
        }
      } yield ()).unsafeRunSync()
    }
  }
}
