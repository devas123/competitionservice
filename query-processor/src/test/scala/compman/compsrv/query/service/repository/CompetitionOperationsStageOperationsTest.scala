package compman.compsrv.query.service.repository

import cats.effect.IO
import compman.compsrv.SpecBase
import compman.compsrv.logic.actors.behavior.WithIORuntime

import scala.util.Using

class CompetitionOperationsStageOperationsTest
    extends SpecBase with TestEntities with EmbeddedMongoDb with WithIORuntime {
  test("should save stage") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _ <- CompetitionUpdateOperations[IO].removeCompetitionState(competitionId)
        _ <- ManagedCompetitionsOperations.addManagedCompetition[IO](managedCompetition)
        _     <- CompetitionUpdateOperations[IO].addStage(stageDescriptor)
        stage <- CompetitionQueryOperations[IO].getStageById(competitionId)(stageId)
      } yield assert(stage.isDefined)).unsafeRunSync()
    }
  }
}
