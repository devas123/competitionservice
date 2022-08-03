package compman.compsrv.query.service.repository

import cats.effect.IO
import compman.compsrv.SpecBase
import compman.compsrv.logic.actors.behavior.WithIORuntime

import scala.util.Using

class ManagedCompetitionsOperationsTest extends SpecBase with EmbeddedMongoDb with TestEntities with WithIORuntime {

  test("Managed competitions operations test") {
    val result = Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      ManagedCompetitionsOperations.addManagedCompetition[IO](managedCompetition).unsafeRunSync()
      val competitions = ManagedCompetitionsOperations.getActiveCompetitions[IO].unsafeRunSync()
      ManagedCompetitionsOperations.deleteManagedCompetition[IO](managedCompetition.id).unsafeRunSync()
      val shouldBeEmpty = ManagedCompetitionsOperations.getActiveCompetitions[IO].unsafeRunSync()
      println("Dadadada")
      println(competitions)
      assert(competitions.nonEmpty)
      assert(shouldBeEmpty.isEmpty)
    }

    result.get
  }
}
