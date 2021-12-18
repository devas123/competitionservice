package compman.compsrv.logic.actors

import zio.{ZIO, ZLayer}
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.TestAspect._

object ActorSystemSpec extends DefaultRunnableSpec {
  import Utils._
  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()
  private val testActorName                                 = "testActor"

  override def spec: ZSpec[TestEnvironment, Any] =
    (suite("Actor system")(
      testM("Should create actor and remove actor") {
        for {
          actorSystem <- ActorSystem("test")
          _           <- createTestActor(actorSystem, testActorName)
          exists      <- actorSystem.select[Msg]("/" + testActorName).fold(_ => None, Some(_))
          _           <- ZIO.sleep(3.seconds)
          exists2     <- actorSystem.select[Msg](testActorName).fold(_ => None, Some(_))
        } yield assert(exists)(isSome) && assert(exists2)(isNone)
      },
      testM("Should send messages to deadLetter if actor is stopped") {
        for {
          actorSystem <- ActorSystem("test")
          actor       <- createTestActor(actorSystem, testActorName)
          exists      <- actorSystem.select[Msg]("/" + testActorName).fold(_ => None, Some(_))
          _           <- ZIO.sleep(3.seconds)
          _           <- actor ! Test
          _           <- actor ! Test
          _           <- actor ! Test
          _           <- actor ! Test
          exists2     <- actorSystem.select[Msg](testActorName).fold(_ => None, Some(_))
          _           <- ZIO.sleep(1.seconds)
        } yield assert(exists)(isSome) && assert(exists2)(isNone)
      },
      testM("Should restart actor if it fails.") {
        for {
          actorSystem <- ActorSystem("test")
          actor       <- createFailingActor(actorSystem, testActorName)
          exists      <- actorSystem.select[Msg]("/" + testActorName).fold(_ => None, Some(_))
          _           <- actor ! Test
          _           <- actor ! Test
          _           <- ZIO.sleep(1.seconds)
          exists2     <- actorSystem.select[Msg](testActorName).fold(_ => None, Some(_))
        } yield assert(exists)(isSome) && assert(exists2)(isSome)
      },
      testM("Should stop children when the actor stops.") {
        for {
          actorSystem <- ActorSystem("test")
          mainActor   <- createMainActor(actorSystem, testActorName)
          receiver    <- TestKit[Msg](actorSystem)
          _           <- actorSystem.eventStream.subscribe[Test.type](receiver.ref)
          exists      <- actorSystem.select[Msg]("/" + testActorName).fold(_ => None, Some(_))
          _           <- receiver.watchWith(Fail, mainActor)
          _           <- ZIO.sleep(1.seconds)
          _           <- mainActor ! Stop
          _           <- ZIO.sleep(1.seconds)
          exists2     <- actorSystem.select[Msg](testActorName).fold(_ => None, Some(_))
          _           <- receiver.expectOneOf(3.seconds, classOf[Fail.type], classOf[Test.type])
          _           <- receiver.expectOneOf(3.seconds, classOf[Fail.type], classOf[Test.type])
          _           <- receiver.expectOneOf(3.seconds, classOf[Fail.type], classOf[Test.type])
        } yield assert(exists)(isSome) && assert(exists2)(isNone)
      },
      testM("Should stop children when the actor stops for eventSourced.") {
        for {
          actorSystem <- ActorSystem("test")
          mainActor   <- createMainActorEventSourced(actorSystem, testActorName)
          receiver    <- TestKit[Msg](actorSystem)
          _           <- actorSystem.eventStream.subscribe[Test.type](receiver.ref)
          exists      <- actorSystem.select[Msg]("/" + testActorName).fold(_ => None, Some(_))
          _           <- receiver.watchWith(Fail, mainActor)
          _           <- ZIO.sleep(1.seconds)
          _           <- mainActor ! Stop
          _           <- ZIO.sleep(1.seconds)
          exists2     <- actorSystem.select[Msg](testActorName).fold(_ => None, Some(_))
          _           <- receiver.expectOneOf(3.seconds, classOf[Fail.type], classOf[Test.type])
          _           <- receiver.expectOneOf(3.seconds, classOf[Fail.type], classOf[Test.type])
          _           <- receiver.expectOneOf(3.seconds, classOf[Fail.type], classOf[Test.type])
        } yield assert(exists)(isSome) && assert(exists2)(isNone)
      }
    ) @@ sequential).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
