package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.{URIO, ZIO, ZLayer}
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object CompetitionOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  import EmbeddedMongoDb._
  override def spec: ZSpec[Any, Throwable] = suite("competition operations")(
    testM("query should return none when there are no competitions") {
      (for {
        _     <- CompetitionUpdateOperations[LIO].removeCompetitionProperties("managedCompetition")
        props <- CompetitionQueryOperations.getCompetitionProperties("managedCompetition")
      } yield assert(props)(isNone)).provideLayer(layers)
    },
    testM("should save competition") {
      (for {
        _     <- CompetitionUpdateOperations[LIO].addCompetitionProperties(competitionProperties)
        props <- CompetitionQueryOperations.getCompetitionProperties(competitionId)
      } yield assert(props)(isSome)).provideLayer(layers)
    },
    testM("should save category") {
      (for {
        _        <- CompetitionUpdateOperations[LIO].addCategory(category)
        category <- CompetitionQueryOperations.getCategoryById(competitionId)(categoryId)
      } yield assert(category)(isSome)).provideLayer(layers)    }
  ) @@ sequential @@ aroundAll(ZIO.effect(startEmbeddedMongo()))(srv => URIO(srv._1.stop()))
}
