package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.PendingMessage
import zio.{Promise, Queue, Task}

final case class ActorRef[Msg[+_]](private val queue: Queue[PendingMessage[Msg, _]], private val path: String)(
  private val postStop: () => Task[Unit]
) {
  def ![A](fa: Msg[A]): Task[Unit] = for {
    promise <- Promise.make[Throwable, A]
    _       <- queue.offer((fa, promise))
  } yield ()

  def ?[A](fa: Msg[A]): Task[A] = for {
    promise <- Promise.make[Throwable, A]
    _       <- queue.offer((fa, promise))
    res     <- promise.await
  } yield res

  private[actors] val stop: Task[List[_]] = for {
    tail <- queue.takeAll
    _    <- queue.shutdown
    _    <- postStop()
  } yield tail

  override def toString: String = s"ActorRef($path)"
}
