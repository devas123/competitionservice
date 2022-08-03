package compman.compsrv.query.service.repository

import cats.effect.IO
import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.SpecBase
import compman.compsrv.logic.actors.behavior.WithIORuntime

import scala.util.Using

class CompetitionOperationsTest extends SpecBase with TestEntities with EmbeddedMongoDb with WithIORuntime {

  test("should delete competition and query should return none when there are no competitions") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _     <- CompetitionUpdateOperations[IO].removeCompetitionState(competitionId)
        props <- CompetitionQueryOperations.getCompetitionProperties(competitionId)
      } yield assert(props.isEmpty)).unsafeRunSync()
    }
  }
  test("should save competition") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _     <- CompetitionUpdateOperations[IO].removeCompetitionState(competitionId)
        _     <- ManagedCompetitionsOperations.addManagedCompetition[IO](managedCompetition)
        props <- CompetitionQueryOperations.getCompetitionProperties(competitionId)
      } yield assert(props.isDefined)).unsafeRunSync()
    }
  }
  test("should save and load competitor") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import cats.implicits._
      import context._
      (for {
        _ <- CompetitionUpdateOperations[IO].removeCompetitorsForCompetition(competitionId)
        competitors = CompetitorService.generateRandomCompetitorsForCategory(5, 5, categoryId, competitionId)
        _   <- competitors.traverse(c => CompetitionUpdateOperations[IO].addCompetitor(DtoMapping.mapCompetitor(c)))
        res <- CompetitionQueryOperations[IO].getCompetitorsByCompetitionId(competitionId)(None)
        (loadedCompetitorsByCompetitionId, pagination) = res
        loadedCompetitorsByIds <- competitors
          .traverse(c => CompetitionQueryOperations[IO].getCompetitorById(competitionId)(c.id))
          .map(_.mapFilter(identity))
        _ <- IO {
          assert(loadedCompetitorsByCompetitionId.size == competitors.size)
          assert(loadedCompetitorsByCompetitionId.forall(c => c.lastName.nonEmpty && c.firstName.nonEmpty))
          assert(loadedCompetitorsByIds.size == competitors.size)
          assert(pagination.totalResults == competitors.size)
        }
      } yield ()).unsafeRunSync()
    }
  }
  test("should save category") {
    Using(embeddedMongo()) { mongo =>
      val context = EmbeddedMongoDb.context(mongo.getFirstMappedPort.intValue())
      import context._
      (for {
        _        <- CompetitionUpdateOperations[IO].removeCompetitionState(competitionId)
        _        <- ManagedCompetitionsOperations.addManagedCompetition[IO](managedCompetition)
        _        <- CompetitionUpdateOperations[IO].addCategory(category)
        category <- CompetitionQueryOperations.getCategoryById(competitionId)(categoryId)
      } yield assert(category.isDefined)).unsafeRunSync()
    }
  }
}
