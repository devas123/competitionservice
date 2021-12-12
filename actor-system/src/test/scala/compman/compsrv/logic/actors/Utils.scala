package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.clock.Clock
import zio.console.putStrLn
import zio.duration.durationInt
import zio.test.environment.TestEnvironment
import zio.{Fiber, RIO}

object Utils {

  sealed trait Msg
  object Test extends Msg
  object Stop extends Msg

  def testActorBehavior(dieAfter: Option[Int]): ActorBehavior[TestEnvironment, Unit, Msg] = new ActorBehavior[TestEnvironment, Unit, Msg] {
    override def init(
                       actorConfig: ActorConfig,
                       context: Context[Msg],
                       initState: Unit,
                       timers: Timers[TestEnvironment, Msg]
                     ): RIO[TestEnvironment, (Seq[Fiber[Throwable, Unit]], Seq[Msg], Unit)] =
      for {_ <- dieAfter.map(sec => timers.startSingleTimer("stop", sec.seconds, Stop).unit).getOrElse(RIO.unit)} yield (Seq.empty, Seq.empty, ())

    override def receive(
                          context: Context[Msg],
                          actorConfig: ActorConfig,
                          state: Unit,
                          command: Msg,
                          timers: Timers[TestEnvironment, Msg]
                        ): RIO[TestEnvironment, Unit] =
      command match {
        case Stop => putStrLn("Stopping").unit *> context.stopSelf.unit
        case Test => putStrLn("Test").unit
      }
  }

  def createTestActor(actorSystem: ActorSystem, name: String, dieAfter: Option[Int] = Some(2)): RIO[TestEnvironment with Clock, ActorRef[Msg]] =
    actorSystem.make(name, ActorConfig(), (), testActorBehavior(dieAfter))
}
