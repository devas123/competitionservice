package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.PendingMessage
import compman.compsrv.logic.actors.dungeon.SystemMessage
import zio.{Promise, Queue, Task, ZIO}

final case class ActorRef[Msg[+_]](private val queue: Queue[PendingMessage[Msg, _]], private val path: ActorPath)(
  private val postStop: () => Task[Unit],
) {

  override def hashCode(): Int = path.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case x: ActorRef[_] => path.uid == x.path.uid && path == x.path
    case _              => false
  }

  private[actors] def sendSystemMessage[A](msg: SystemMessage[A]): Task[Unit] = for {
    promise  <- Promise.make[Throwable, A]
    shutdown <- queue.isShutdown
    _        <- if (shutdown) ZIO.fail(new RuntimeException("Actor stopped")) else queue.offer((Left(msg), promise))
  } yield ()

  def ![A](fa: Msg[A]): Task[Unit] = for {
    promise  <- Promise.make[Throwable, A]
    shutdown <- queue.isShutdown
    _        <- if (shutdown) ZIO.fail(new RuntimeException("Actor stopped")) else queue.offer((Right(fa), promise))
  } yield ()

  def ?[A](fa: Msg[A]): Task[A] = for {
    promise <- Promise.make[Throwable, A]
    _       <- queue.offer((Right(fa), promise))
    res     <- promise.await
  } yield res

  private[actors] val stop: Task[List[_]] = for {
    _          <- postStop()
    tail       <- queue.takeAll
    _          <- queue.shutdown
  } yield tail

  override def toString: String = s"ActorRef($path)"
}
