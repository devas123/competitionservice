package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.Terminated
import zio.{ZIO, ZLayer}
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

object DeathWatchSpec extends DefaultRunnableSpec {

  import Utils._

  sealed trait WatcherDsl

  final case object DeathNotify extends WatcherDsl

  final case object Unwatch extends WatcherDsl

  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()

  import Behaviors._

  def watchingBehavior[F](
    actorToWatch: ActorRef[F],
    msg: Option[WatcherDsl]
  ): ActorBehavior[TestEnvironment, Unit, WatcherDsl] = Behaviors.behavior[TestEnvironment, Unit, WatcherDsl]
    .withInit { (_, context, _, _) =>
      for {_ <- msg.map(context.watchWith(_, actorToWatch)).getOrElse(context.watch(actorToWatch))} yield (
        Seq.empty,
        Seq.empty,
        ()
      )
    }.withReceive((context, _, _, command, _) =>
    command match {
      case DeathNotify => putStrLn("Death notify!!").unit *> context.stopSelf.unit
      case Unwatch => putStrLn("Unwatch actor") *> context.unwatch(actorToWatch)
    }
  ).withReceiveSignal((context, _, _, _, _) => {
    case x: Terminated => putStrLn(s"Terminated msg: $x").unit *> context.stopSelf.unit
  })

  override def spec: ZSpec[TestEnvironment, Any] = suite("DeathWatch")(
    testM("Should react to actor death with custom message.") {
      for {
        actorSystem <- ActorSystem("test")
        dieAfter = 1
        watchee <- createTestActor(actorSystem, "testActor", Option(dieAfter))
        _ <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify)))
        _ <- ZIO.sleep((dieAfter + 1).seconds)
        msg <- actorSystem.select[Any]("watcher").isFailure
      } yield assert(msg)(isTrue)
    },
    testM("Should handle unwatch.") {
      for {
        actorSystem <- ActorSystem("test")
        dieAfter = 1
        watchee <- createTestActor(actorSystem, "testActor", Option(dieAfter))
        watcher <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify)))
        _ <- ZIO.sleep((dieAfter - 1).seconds)
        _ <- watcher ! Unwatch
        _ <- ZIO.sleep((dieAfter + 1).seconds)
        msg <- actorSystem.select[Any]("watcher")
      } yield assert(msg)(not(isNull))
    },
    testM("Should react to actor death with Terminated message.") {
      for {
        actorSystem <- ActorSystem("test")
        dieAfter = 1
        watchee <- createTestActor(actorSystem, "testActor", Option(dieAfter))
        _ <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, None))
        _ <- ZIO.sleep((dieAfter + 1).seconds)
        msg <- actorSystem.select[Any]("watcher").isFailure
      } yield assert(msg)(isTrue)
    }
  ).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
