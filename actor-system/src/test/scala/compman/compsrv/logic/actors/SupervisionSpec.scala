package compman.compsrv.logic.actors

import zio.{ZIO, ZLayer}
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment.TestEnvironment

object SupervisionSpec extends DefaultRunnableSpec {
  import Utils._
  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()
  private val testActorName                                 = "testActor"

  override def spec: ZSpec[TestEnvironment, Any] =
    (suite("Actor system")(
      testM("Should restart actor if it fails with an exception.") {
        ActorSystem("test").use { actorSystem =>
          for {
            actor <- createUnexpectedFailingActor(actorSystem, testActorName)
            exists <- actorSystem.select[Msg]("/" + testActorName).fold(_ => None, Some(_))
            _ <- actor ! Test
            _ <- ZIO.sleep(1.seconds)
            exists2 <- actorSystem.select[Msg](testActorName).fold(_ => None, Some(_))
            _ <- ZIO.sleep(1.seconds)
            _ <- actor ! Test
            _ <- ZIO.sleep(1.seconds)
          } yield assert(exists)(isSome) && assert(exists2)(isSome)
        }
      }
    ) @@ sequential).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
