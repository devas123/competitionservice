package compman.compsrv.logic.actors

import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.Logging
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{ZIO, ZLayer}


object EventStreamSpec extends DefaultRunnableSpec {
  import Utils._
  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()

  override def spec: ZSpec[TestEnvironment, Any] = suite("Event stream")(
    testM("Should subscribe and publish messages") {
      ActorSystem("test").use { actorSystem =>
        for {
          actor <- TestKit[Msg](actorSystem)
          _ <- actorSystem.eventStream.subscribe[Test.type](actor.ref)
          _ <- ZIO.sleep(1.seconds)
          _ <- actorSystem.eventStream.publish(Test)
          msg <- actor.expectMessage(1.seconds)
        } yield assert(msg)(isSome)
      }
    },
    testM("Should subscribe and publish messages to multiple subscribers") {
      ActorSystem("test").use { actorSystem =>
        for {
          actor <- TestKit[Msg](actorSystem)
          actor2 <- TestKit[Msg](actorSystem)
          _ <- actorSystem.eventStream.subscribe[Test.type](actor.ref)
          _ <- actorSystem.eventStream.subscribe[Test.type](actor2.ref)
          _ <- ZIO.sleep(1.seconds)
          _ <- actorSystem.eventStream.publish(Test)
          msg1 <- actor.expectMessage(1.seconds)
          msg2 <- actor2.expectMessage(1.seconds)
        } yield assert(msg1)(isSome) && assert(msg2)(isSome)
      }
    },
    testM("Should unsubscribe") {
      ActorSystem("test").use { actorSystem =>
        for {
          actor <- TestKit[Msg](actorSystem)
          actor2 <- TestKit[Msg](actorSystem)
          _ <- actorSystem.eventStream.subscribe[Test.type](actor.ref)
          _ <- actorSystem.eventStream.subscribe[Test.type](actor2.ref)
          _ <- ZIO.sleep(1.seconds)
          _ <- actorSystem.eventStream.publish(Test)
          msg1 <- actor.expectMessage(1.seconds)
          msg2 <- actor2.expectMessage(1.seconds)
          _ <- actorSystem.eventStream.unsubscribe[Test.type](actor.ref)
          _ <- actorSystem.eventStream.publish(Test)
          msg3 <- actor.expectMessage(1.seconds)
          msg4 <- actor2.expectMessage(1.seconds)
        } yield
          assert(msg1)(isSome) &&
            assert(msg2)(isSome) &&
            assert(msg3)(isNone) &&
            assert(msg4)(isSome)
      }
    }
  ).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
