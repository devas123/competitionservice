package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.PendingMessage
import compman.compsrv.logic.actors.dungeon.{DeathWatch, SystemMessage, Unwatch, Watch}
import zio.{Promise, Queue, Ref, Task, ZIO}

final case class ActorRef[Msg[+_]](private val queue: Queue[PendingMessage[Msg, _]], private val path: ActorPath)(
  private val systemMessagesQueue: Queue[PendingMessage[SystemMessage, _]],
  private val postStop: () => Task[Unit],
  override var watching: Ref[Map[ActorRef[_], Option[Any]]],
  override var watchedBy: Ref[Set[ActorRef[_]]],
  override var terminatedQueued: Ref[Map[ActorRef[_], Option[Any]]],
) extends DeathWatch {


  override def hashCode(): Int = path.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case x : ActorRef[_] => path.uid == x.path.uid && path == x.path
    case _ => false
  }

  def ![A](fa: Msg[A]): Task[Unit] = for {
    promise <- Promise.make[Throwable, A]
    shutdown <- queue.isShutdown
    _       <- if (shutdown) ZIO.fail(new RuntimeException("Actor stopped")) else queue.offer((fa, promise))
  } yield ()

  def ?[A](fa: Msg[A]): Task[A] = for {
    promise <- Promise.make[Throwable, A]
    _       <- queue.offer((fa, promise))
    res     <- promise.await
  } yield res

  private[actors] val stop: Task[List[_]] = for {
    _    <- postStop()
    systemTail <- systemMessagesQueue.takeAll
    tail <- queue.takeAll
    _    <- queue.shutdown
    _    <- systemMessagesQueue.shutdown
  } yield systemTail ++ tail

  protected def specialHandle[A](msg: Msg[A]): Boolean = msg match {
    case w: Watch[_, _] =>
      true
    //        w.watcher.sendSystemMessage(
//          DeathWatchNotification(w.watchee, existenceConfirmed = false, addressTerminated = false))
    case _: Unwatch[_, _] => true // Just ignore
    case _ => false
  }


  override def toString: String = s"ActorRef($path)"
}
