package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.logging.Logging
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Fiber, RIO, ZIO, ZLayer}


object DeathWatchSpec extends DefaultRunnableSpec {

  import Utils._

  sealed trait WatcherDsl
  final case object DeathNotify extends WatcherDsl

  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()

  def watchingBehavior[F](actorToWatch: ActorRef[F]): ActorBehavior[TestEnvironment, Unit, WatcherDsl] = new ActorBehavior[TestEnvironment, Unit, WatcherDsl] {
    override def init(
                       actorConfig: ActorConfig,
                       context: Context[WatcherDsl],
                       initState: Unit,
                       timers: Timers[TestEnvironment, WatcherDsl]
                     ): RIO[TestEnvironment, (Seq[Fiber[Throwable, Unit]], Seq[WatcherDsl], Unit)] =
      for {_ <- context.watchWith(DeathNotify, actorToWatch)} yield (Seq.empty, Seq.empty, ())

    override def receive(
                          context: Context[WatcherDsl],
                          actorConfig: ActorConfig,
                          state: Unit,
                          command: WatcherDsl,
                          timers: Timers[TestEnvironment, WatcherDsl]
                        ): RIO[TestEnvironment, Unit] =
      command match {
        case DeathNotify => putStrLn("Death notify!!").unit *> context.stopSelf.unit
      }
  }


  override def spec: ZSpec[TestEnvironment, Any] = suite("DeathWatch")(
    testM("Should react to actor death.") {
      for {
        actorSystem <- ActorSystem("test")
        dieAfter = 1
        watchee <- createTestActor(actorSystem, "testActor", Option(dieAfter))
        _ <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee))
        _ <- ZIO.sleep((dieAfter + 1).seconds)
        msg <- actorSystem.select[Any]("watcher").isFailure
      } yield assert(msg)(isTrue)
    }
  ).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
