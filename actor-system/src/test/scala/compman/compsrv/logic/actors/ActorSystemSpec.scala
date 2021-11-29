package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import org.junit.runner.RunWith
import zio.{Fiber, RIO, ZIO}
import zio.clock.Clock
import zio.duration.durationInt
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

sealed trait Msg[+A]
object Stop extends Msg[Unit]

@RunWith(classOf[zio.test.junit.ZTestJUnitRunner])
class ActorSystemSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] = suite("Actor system")(testM("Should create actor and remove actor") {
    for {
      actorSystem <- ActorSystem("test")
      behavior = new ActorBehavior[TestEnvironment, Unit, Msg] {
        override def init(
          actorConfig: ActorSystem.ActorConfig,
          context: Context[Msg],
          initState: Unit,
          timers: Timers[TestEnvironment, Msg]
        ): RIO[TestEnvironment, (Seq[Fiber[Throwable, Unit]], Seq[Msg[Any]], Unit)] =
          for { _ <- timers.startSingleTimer("stop", 1.seconds, Stop) } yield (Seq.empty, Seq.empty, ())
        override def receive[A](
          context: Context[Msg],
          actorConfig: ActorSystem.ActorConfig,
          state: Unit,
          command: Msg[A],
          timers: Timers[TestEnvironment, Msg]
        ): RIO[TestEnvironment, (Unit, A)] = command match {
          case Stop => ZIO.effect(println("Stopping")) *> context.stopSelf.map(_ => ((), ().asInstanceOf[A]))
        }
      }
      testActor = "testActor"
      _ <- actorSystem.make(testActor, ActorConfig(), (), behavior)
      exists <- actorSystem.select[Msg]("/testActor").fold(_ => None, Some(_))
      _ <- ZIO.sleep(2.seconds)
      exists2 <- actorSystem.select[Msg](testActor).fold(_ => None, Some(_))
    } yield assert(exists)(isSome) && assert(exists2)(isNone)
  }).provideSomeLayer[TestEnvironment](Clock.live)
}
