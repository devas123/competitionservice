package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.Terminated
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.logging.Logging
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{ZIO, ZLayer}

object DeathWatchSpec extends DefaultRunnableSpec {

  import Utils._

  sealed trait WatcherDsl

  final case object DeathNotify extends WatcherDsl

  final case object Unwatch extends WatcherDsl

  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()
  final val dieAfterSeconds                                 = 2

  import Behaviors._

  def waitForActorToDie(actorSystem: ActorSystem, name: String, timeoutSeconds: Int = 10): ZIO[Clock, Nothing, Unit] =
    for {
      actorDead <- actorSystem.select[Any](name).isFailure
      _ <- waitForActorToDie(actorSystem, name, timeoutSeconds - 1).delay(1.seconds)
        .unless(timeoutSeconds > 0 && actorDead)
    } yield ()

  def watchingBehavior[F](
    actorToWatch: ActorRef[F],
    msg: Option[WatcherDsl]
  ): ActorBehavior[TestEnvironment, Unit, WatcherDsl] = Behaviors.behavior[TestEnvironment, Unit, WatcherDsl]
    .withInit { (_, context, _, _) =>
      for { _ <- msg.map(context.watchWith(_, actorToWatch)).getOrElse(context.watch(actorToWatch)) } yield (
        Seq.empty,
        Seq.empty,
        ()
      )
    }.withReceive((context, _, _, command, _) =>
      command match {
        case DeathNotify => putStrLn("Death notify!!").unit *> context.stopSelf.unit
        case Unwatch     => putStrLn("Unwatch actor") *> context.unwatch(actorToWatch)
      }
    ).withReceiveSignal((context, _, _, _, _) => { case x: Terminated =>
      putStrLn(s"Terminated msg: $x").unit *> context.stopSelf.unit
    })

  private val timeDeltaPlusSeconds = 2

  override def spec: ZSpec[TestEnvironment, Any] = suite("DeathWatch")(
    testM("Should react to actor death with custom message.") {
      ActorSystem("test").use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "testActor", Option(dieAfterSeconds))
          _       <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify)))
          _       <- waitForActorToDie(actorSystem, "testActor")
          _       <- ZIO.sleep(timeDeltaPlusSeconds.seconds)
          msg     <- actorSystem.select[Any]("watcher").isFailure
        } yield assertTrue(msg)
      }
    },
    testM("Should react to actor death with custom message when the actor is already dead.") {
      ActorSystem("test").use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "testActor", Option(dieAfterSeconds))
          _       <- for { _ <- actorSystem.select[Any]("watcher").isFailure } yield ()
          _       <- waitForActorToDie(actorSystem, "testActor")
          _       <- ZIO.debug("Creating watcher.")
          _       <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify)))
          _       <- ZIO.sleep(timeDeltaPlusSeconds.seconds)
          actorDoesNotExist <- actorSystem.select[Any]("watcher").isFailure
        } yield assertTrue(actorDoesNotExist)
      }
    },
    testM("Should handle unwatch.") {
      ActorSystem("test").use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "testActor", Option(dieAfterSeconds))
          watcher <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify)))
          _       <- ZIO.sleep((dieAfterSeconds - 1).seconds)
          _       <- watcher ! Unwatch
          _       <- ZIO.sleep((dieAfterSeconds + timeDeltaPlusSeconds).seconds)
          msg     <- actorSystem.select[Any]("watcher")
        } yield assertTrue(msg != null)
      }
    },
    testM("Should react to actor death with Terminated message.") {
      ActorSystem("test").use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "testActor", Option(dieAfterSeconds))
          _       <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, None))
          _       <- waitForActorToDie(actorSystem, "testActor")
          _       <- ZIO.sleep(timeDeltaPlusSeconds.seconds)
          msg     <- actorSystem.select[Any]("watcher").isFailure
        } yield assertTrue(msg)
      }
    }
  ).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
