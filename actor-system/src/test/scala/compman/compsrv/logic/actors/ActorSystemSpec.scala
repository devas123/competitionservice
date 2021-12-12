package compman.compsrv.logic.actors

import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.Logging
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{ZIO, ZLayer}


object ActorSystemSpec extends DefaultRunnableSpec {
  import Utils._
  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()
  private val testActor = "testActor"

  override def spec: ZSpec[TestEnvironment, Any] = suite("Actor system")(
    testM("Should create actor and remove actor") {
      for {
        actorSystem <- ActorSystem("test")
        _ <- createTestActor(actorSystem, testActor)
        exists <- actorSystem.select[Msg]("/" + testActor).fold(_ => None, Some(_))
        _ <- ZIO.sleep(3.seconds)
        exists2 <- actorSystem.select[Msg](testActor).fold(_ => None, Some(_))
      } yield assert(exists)(isSome) && assert(exists2)(isNone)
    },
    testM("Should send messages to deadLetter if actor is stopped") {
      for {
        actorSystem <- ActorSystem("test")
        actor <- createTestActor(actorSystem, testActor)
        exists <- actorSystem.select[Msg]("/" + testActor).fold(_ => None, Some(_))
        _ <- ZIO.sleep(3.seconds)
        exists2 <- actorSystem.select[Msg](testActor).fold(_ => None, Some(_))
        _ <- actor ! Test
        _ <- actor ! Test
        _ <- actor ! Test
        _ <- actor ! Test
      } yield assert(exists)(isSome) && assert(exists2)(isNone)
    }).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
