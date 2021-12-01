package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import org.junit.runner.RunWith
import zio.{URIO, ZIO, ZLayer}
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class CompetitionOperationsStageOperationsTest extends DefaultRunnableSpec with TestEntities with EmbeddedMongoDb {
  type Env = Logging
  val layers: ZLayer[Any, Throwable, Env] = CompetitionLogging.Live.loggingLayer
  import EmbeddedMongoDb._
  override def spec: ZSpec[Any, Throwable] = suite("competition operations")(testM("should save stage") {
    (for { _ <- CompetitionUpdateOperations[LIO].addStage(stageDescriptor) } yield assert(Some(()))(isSome))
      .provideLayer(layers)
  }) @@ aroundAll(ZIO.effect(startEmbeddedMongo()))(tuple => URIO(tuple._1.stop()))
}
