package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.EventSourcedMessages.EventSourcingCommand
import zio.{Fiber, RIO, URIO, ZIO}
import zio.clock.Clock
import zio.console.putStrLn
import zio.duration.durationInt
import zio.test.environment.TestEnvironment

object Utils {

  sealed trait Msg
  object Test extends Msg
  object Fail extends Msg
  object Stop extends Msg
  import Behaviors._

  def testActorBehavior(dieAfter: Option[Int]): ActorBehavior[TestEnvironment, Unit, Msg] = Behaviors
    .behavior[TestEnvironment, Unit, Msg].withInit { (_, _, _, timers) =>
      for {
        _ <- dieAfter.map(sec => timers.startSingleTimer("stop", sec.seconds, Stop).unit).getOrElse(RIO.unit)
      } yield (Seq.empty, Seq.empty, ())
    }.withReceive { (context, _, _, command, _) =>
      command match {
        case Stop => putStrLn(s"Stopping ${context.self}").unit *> context.stopSelf.unit
        case Test => putStrLn(s"Test ${context.self}").unit
        case Fail => putStrLn(s"Fail ${context.self}").unit
      }
    }

  def failingActorBehavior(): ActorBehavior[TestEnvironment, Unit, Msg] = Behaviors.behavior[TestEnvironment, Unit, Msg]
    .withReceive { (context, _, _, command, _) =>
      command match {
        case Stop => putStrLn("Stopping").unit *> context.stopSelf.unit
        case Test => putStrLn("Failing") *> ZIO.fail(new RuntimeException("Test exception"))
        case Fail => putStrLn("Interrupting") *> ZIO.interrupt
      }
    }

  def unexpectedFailingActorBehavior(): ActorBehavior[TestEnvironment, Unit, Msg] = Behaviors.behavior[TestEnvironment, Unit, Msg]
    .withReceive { (context, _, _, command, _) =>
      command match {
        case Stop => putStrLn("Stopping").unit *> context.stopSelf.unit
        case Test => throw new Exception("Test fail.")
        case Fail => putStrLn("Interrupting") *> ZIO.interrupt
      }
    }

  def mainActorBehavior(): ActorBehavior[TestEnvironment, Unit, Msg] = Behaviors.behavior[TestEnvironment, Unit, Msg]
    .withReceive { (context, _, _, command, _) =>
      command match {
        case Stop => putStrLn("Main Stopping").unit *> context.stopSelf.unit
        case Test => putStrLn("Main Failing") *> ZIO.fail(new RuntimeException("Test exception"))
        case Fail => putStrLn("Main Interrupting") *> ZIO.interrupt
      }
    }.withInit { (_, context, _, _) =>
      context.make("child", ActorConfig(), (), childActorBehavior(true)).as((Seq.empty, Seq.empty, ()))
    }

  sealed trait Events
  final case object MockEvent extends Events

  def mainActorBehaviorEventSourced(): EventSourcedBehavior[TestEnvironment, Unit, Msg, Events] =
    new EventSourcedBehavior[TestEnvironment, Unit, Msg, Events]("123") {
      private val u: (EventSourcedMessages.EventSourcingCommand[Events], Unit => Unit) = (EventSourcingCommand.ignore, _ => ())
      override def receive(
        context: Context[Msg],
        actorConfig: ActorConfig,
        state: Unit,
        command: Msg,
        timers: Timers[TestEnvironment, Msg]
      ): RIO[TestEnvironment, (EventSourcedMessages.EventSourcingCommand[Events], Unit => Unit)] = command match {
        case Stop => (putStrLn("Main Stopping").unit *> context.stopSelf.unit).as(u)
        case Test => putStrLn("Main Failing") *> ZIO.fail(new RuntimeException("Test exception"))
        case Fail => putStrLn("Main Interrupting") *> ZIO.interrupt
      }

      override def sourceEvent(state: Unit, event: Events): RIO[TestEnvironment, Unit] = RIO.unit

      override def getEvents(persistenceId: String, state: Unit): RIO[TestEnvironment, Seq[Events]] = RIO
        .effectTotal(Seq.empty)

      override def persistEvents(persistenceId: String, events: Seq[Events]): URIO[TestEnvironment, Unit] = URIO.unit

      override def init(
        actorConfig: ActorConfig,
        context: Context[Msg],
        initState: Unit,
        timers: Timers[TestEnvironment, Msg]
      ): RIO[TestEnvironment, (Seq[Fiber[Throwable, Unit]], Seq[Msg])] = context
        .make("child", ActorConfig(), (), childActorBehavior(true))
        .as((Seq.empty, Seq.empty))
    }

  def childActorBehavior(createInner: Boolean): ActorBehavior[TestEnvironment, Unit, Msg] = Behaviors
    .behavior[TestEnvironment, Unit, Msg].withReceive { (context, _, _, command, _) =>
      command match {
        case Stop => putStrLn(s"Child Stopping ${context.self}").unit *> context.stopSelf.unit
        case Test => putStrLn(s"Child Failing ${context.self}") *> ZIO.fail(new RuntimeException("Test exception"))
        case Fail => putStrLn(s"Child Interrupting ${context.self}") *> ZIO.interrupt
      }
    }.withPostStop { (_, ctx, _, _) =>
      ctx.actorSystem.eventStream.publish(Test) *> putStrLn(s"Child Stopped! ${ctx.self}")
    }.withInit { (_, context, _, _) =>
      context.make("innerChild", ActorConfig(), (), childActorBehavior(false)).when(createInner)
        .as((Seq.empty, Seq.empty, ()))
    }

  def createFailingActor(actorSystem: ActorSystem, name: String): RIO[TestEnvironment with Clock, ActorRef[Msg]] =
    actorSystem.make(name, ActorConfig(), (), failingActorBehavior())

  def createUnexpectedFailingActor(actorSystem: ActorSystem, name: String): RIO[TestEnvironment with Clock, ActorRef[Msg]] =
    actorSystem.make(name, ActorConfig(), (), unexpectedFailingActorBehavior())

  def createMainActor(actorSystem: ActorSystem, name: String): RIO[TestEnvironment with Clock, ActorRef[Msg]] =
    actorSystem.make(name, ActorConfig(), (), mainActorBehavior())

  def createMainActorEventSourced(actorSystem: ActorSystem, name: String): RIO[TestEnvironment with Clock, ActorRef[Msg]] =
    actorSystem.make(name, ActorConfig(), (), mainActorBehaviorEventSourced())

  def createTestActor(
    actorSystem: ActorSystem,
    name: String,
    dieAfter: Option[Int] = Some(2)
  ): RIO[TestEnvironment with Clock, ActorRef[Msg]] = actorSystem
    .make(name, ActorConfig(), (), testActorBehavior(dieAfter))
}
