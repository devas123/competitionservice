package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.console.putStrLn
import zio.duration.durationInt
import zio.test.environment.TestEnvironment
import zio.RIO
import zio.clock.Clock

object Utils {

  sealed trait Msg
  object Test extends Msg
  object Stop extends Msg
  import Behaviors._

  def testActorBehavior(dieAfter: Option[Int]): ActorBehavior[TestEnvironment, Unit, Msg] = Behaviors
    .behavior[TestEnvironment, Unit, Msg].withInit { (_, _, _, timers) =>
      for {
        _ <- dieAfter.map(sec => timers.startSingleTimer("stop", sec.seconds, Stop).unit).getOrElse(RIO.unit)
      } yield (Seq.empty, Seq.empty, ())
    }.withReceive { (context, _, _, command, _) =>
      command match {
        case Stop => putStrLn("Stopping").unit *> context.stopSelf.unit
        case Test => putStrLn("Test").unit
      }
    }

  def createTestActor(
    actorSystem: ActorSystem,
    name: String,
    dieAfter: Option[Int] = Some(2)
  ): RIO[TestEnvironment with Clock, ActorRef[Msg]] = actorSystem
    .make(name, ActorConfig(), (), testActorBehavior(dieAfter))
}
