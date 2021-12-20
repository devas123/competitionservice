package compman.compsrv.logic.actors.dungeon

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.dungeon.ActorsShutdownWatcher.{Stop, WatcherMessages}
import zio.clock.Clock
import zio.console.Console
import zio.duration.{Duration, durationInt}
import zio.{Fiber, Promise, RIO, ZIO}

import java.util.concurrent.atomic.AtomicLong

case class ActorsShutdownWatcher[R] private(timeout: Duration = 5.seconds, promise: Promise[Nothing, Unit]) extends ActorBehavior[R with Clock, Set[ActorRef[Nothing]], WatcherMessages] {

  override def init(actorConfig: ActorConfig, context: Context[WatcherMessages], initState: Set[ActorRef[Nothing]], timers: Timers[R with Clock, WatcherMessages]): RIO[R with Clock, (Seq[Fiber[Throwable, Unit]], Seq[WatcherMessages], Set[ActorRef[Nothing]])] =
    ZIO.foreach_(initState)(_.stop) *> timers.startSingleTimer("stop", timeout, Stop).as((Seq.empty, Seq.empty, initState))


  override def receive(context: Context[WatcherMessages], actorConfig: ActorConfig, state: Set[ActorRef[Nothing]], command: WatcherMessages, timers: Timers[R with Clock, WatcherMessages]): RIO[R with Clock, Set[ActorRef[Nothing]]] =
    command match {
      case ActorsShutdownWatcher.Stop => promise.succeed(()) *> context.stopSelf.as(state)
    }

  override def receiveSignal(context: Context[WatcherMessages], actorConfig: ActorConfig, state: Set[ActorRef[Nothing]], command: Signal, timers: Timers[R with Clock, WatcherMessages]): RIO[R with Clock, Set[ActorRef[Nothing]]] =
    command match {
      case Terminated(ref) =>
        val ns = state - ref
        if (ns.nonEmpty) {
          RIO.effectTotal(ns)
        } else {
          promise.succeed(()) *> context.stopSelf.as(ns)
        }
      case _ => ZIO.unit.as(state)
    }
}

object ActorsShutdownWatcher {
  private val id = new AtomicLong()

  sealed trait WatcherMessages

  final case object Stop extends WatcherMessages

  def apply[R](provider: ActorRefProvider, children: Set[ActorRef[Nothing]], promise: Promise[Nothing, Unit]): ZIO[R with Clock with Console, Throwable, ActorRef[WatcherMessages]] =
    provider.make[R with Clock, Set[ActorRef[Nothing]], WatcherMessages](s"Shutdown-watcher-${id.getAndIncrement()}", ActorConfig(), children, ActorsShutdownWatcher(promise = promise))
}
