package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.Terminated
import zio.{ZIO, ZLayer}
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.Logging
import zio.test._
import zio.test.environment.TestEnvironment

object DeathWatchSpec extends DefaultRunnableSpec {

  import Utils._

  sealed trait WatcherDsl

  final case object DeathNotify extends WatcherDsl

  final case object Unwatch extends WatcherDsl

  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console()
  final val timeToLive                                 = 2

  import Behaviors._

  def waitForActorToDie(actorSystem: ActorSystem, name: String, timeoutSeconds: Int = 10): ZIO[Clock, Nothing, Unit] =
    for {
      actorDead <- actorSystem.select[Any](name).isFailure
      _ <- waitForActorToDie(actorSystem, name, timeoutSeconds - 1).delay(1.seconds)
        .unless(timeoutSeconds > 0 && actorDead)
    } yield ()

  def watchingBehavior[F](
    actorToWatch: ActorRef[F],
    msg: Option[WatcherDsl],
    outputStop: Boolean = false,
    name: String = ""
  ): ActorBehavior[TestEnvironment, Unit, WatcherDsl] = Behaviors.behavior[TestEnvironment, Unit, WatcherDsl]
    .withInit { (_, context, _, _) =>
      for { _ <- msg.map(context.watchWith(_, actorToWatch)).getOrElse(context.watch(actorToWatch)) } yield (
        Seq.empty,
        Seq.empty,
        ()
      )
    }.withReceive((context, _, _, command, _) =>
      command match {
        case DeathNotify => ZIO.debug(s"$name Death notify!!") *> context.unwatch(actorToWatch) *> context.stopSelf.unit
        case Unwatch     => ZIO.debug(s"$name Unwatch actor") *> context.unwatch(actorToWatch)
      }
    ).withReceiveSignal((context, _, _, _, _) => { case x: Terminated =>
      ZIO.debug(s"$name Terminated msg: $x") *> context.stopSelf.unit
    })
    .withPostStop((_, _, _, _) => ZIO.debug(s"$name Stopped watcher.").when(outputStop))

  private val timeDeltaPlusSeconds = 2

  override def spec: ZSpec[TestEnvironment, Any] = suite("DeathWatch")(
    testM("Should react to actor death with custom message.") {
      ActorSystem("test").use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "testActor", Option(timeToLive))
          _       <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify)))
          _       <- waitForActorToDie(actorSystem, "testActor")
          _       <- ZIO.sleep(timeDeltaPlusSeconds.seconds)
          msg     <- actorSystem.select[Any]("watcher").isFailure
        } yield assertTrue(msg)
      }
    },
    testM("Should react to actor death with custom message when the actor is already dead.") {
      ActorSystem("DeathWatchCustomMessageActorSystem", debugActors = true).use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "DeathWatchCustomMessageTestActor", Option(timeToLive))
          _       <- waitForActorToDie(actorSystem, "DeathWatchCustomMessageTestActor")
          _       <- ZIO.debug("[DeathWatchCustomMessage] Creating watcher.")
          _       <- actorSystem.make("DeathWatchCustomMessageWatcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify), outputStop = true, "DeathWatchCustomMessageWatcher"))
          _       <- ZIO.debug("[DeathWatchCustomMessage] Created watcher.")
          _       <- ZIO.debug(s"[DeathWatchCustomMessage] Started waiting. ${System.currentTimeMillis() / 1000}")
          _       <- waitForActorToDie(actorSystem, "DeathWatchCustomMessageWatcher")
          _       <- ZIO.debug(s"[DeathWatchCustomMessage] Finished waiting. ${System.currentTimeMillis() / 1000}")
          _       <- ZIO.debug("[DeathWatchCustomMessage] Checking if watcher is stopped.")
          actorDoesNotExist <- actorSystem.select[Any]("DeathWatchCustomMessageWatcher")
            .flatMap(r => ZIO.debug(s"[DeathWatchCustomMessage] Query for watcher returned: $r")).isFailure
          _       <- ZIO.debug("[DeathWatchCustomMessage] Checked if the watcher is stopped.")
        } yield assertTrue(actorDoesNotExist)
      }
    },
    testM("Should handle unwatch.") {
      ActorSystem("test").use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "testActor", Option(timeToLive))
          watcher <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, Some(DeathNotify)))
          _       <- ZIO.sleep((timeToLive - 1).seconds)
          _       <- watcher ! Unwatch
          _       <- ZIO.sleep((timeToLive + timeDeltaPlusSeconds).seconds)
          msg     <- actorSystem.select[Any]("watcher")
        } yield assertTrue(msg != null)
      }
    },
    testM("Should react to actor death with Terminated message.") {
      ActorSystem("test").use { actorSystem =>
        for {
          watchee <- createTestActor(actorSystem, "testActor", Option(timeToLive))
          _       <- actorSystem.make("watcher", ActorConfig(), (), watchingBehavior(watchee, None))
          _       <- waitForActorToDie(actorSystem, "testActor")
          _       <- ZIO.sleep(timeDeltaPlusSeconds.seconds)
          msg     <- actorSystem.select[Any]("watcher").isFailure
        } yield assertTrue(msg)
      }
    }
  ).provideSomeLayerShared[TestEnvironment](Clock.live ++ logging)
}
