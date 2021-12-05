package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import de.flapdoodle.embed.mongo.MongodProcess
import zio.{Has, ULayer, URIO, ZLayer, ZManaged}
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._

object CompetitionOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val mongoLayer: ULayer[Has[(MongodProcess, Int)]] = ZLayer.fromManaged(ZManaged.make(URIO(startEmbeddedMongo()))(_ => URIO(stopServer())))
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  override def spec: ZSpec[Environment, Failure] = suite("competition operations")(
    testM("query should return none when there are no competitions") {
      val context = EmbeddedMongoDb.context
      import context._
      (for {
        _     <- CompetitionUpdateOperations[LIO].removeCompetitionState(competitionId)
        props <- CompetitionQueryOperations.getCompetitionProperties(competitionId)
      } yield assert(props)(isNone)).provideLayer(layers)
    },
    testM("should save competition") {
      val context = EmbeddedMongoDb.context
      import context._
      (for {
        _     <- CompetitionUpdateOperations[LIO].addCompetitionProperties(competitionProperties)
        props <- CompetitionQueryOperations.getCompetitionProperties(competitionId)
      } yield assert(props)(isSome)).provideLayer(layers)
    },
    testM("should save category") {
      val context = EmbeddedMongoDb.context
      import context._
      (for {
        _        <- CompetitionUpdateOperations[LIO].addCategory(category)
        category <- CompetitionQueryOperations.getCategoryById(competitionId)(categoryId)
      } yield assert(category)(isSome)).provideLayer(layers)
    }
  ).provideCustomLayerShared(mongoLayer)
}
