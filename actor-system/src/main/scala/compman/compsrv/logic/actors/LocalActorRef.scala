package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.PendingMessage
import compman.compsrv.logic.actors.dungeon.SystemMessage
import zio.{Promise, Queue, Task, ZIO}

trait ActorRef[Msg] {
  private[actors] def sendSystemMessage(msg: SystemMessage): Task[Unit]
  def !(fa: Msg): Task[Unit]
  private[actors] val stop: Task[List[_]]
}

private[actors] trait MinimalActorRef[Msg] extends ActorRef[Msg] {
  override private[actors] def sendSystemMessage(msg: SystemMessage) = Task.unit
  override def !(fa: Msg): Task[Unit] = Task.unit
  override private[actors] val stop = Task(List.empty)
}

private[actors] case class LocalActorRef[Msg](private val queue: Queue[PendingMessage[Msg]], private val path: ActorPath)(
  private val postStop: () => Task[Unit],
) extends ActorRef[Msg] {

  override def hashCode(): Int = path.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case x: LocalActorRef[_] => path.uid == x.path.uid && path == x.path
    case _              => false
  }

  override private[actors] def sendSystemMessage(msg: SystemMessage): Task[Unit] = for {
    promise  <- Promise.make[Throwable, Unit]
    shutdown <- queue.isShutdown
    _        <- if (shutdown) ZIO.fail(new RuntimeException("Actor stopped")) else queue.offer((Left(msg), promise))
  } yield ()

  override def !(fa: Msg): Task[Unit] = for {
    promise  <- Promise.make[Throwable, Unit]
    shutdown <- queue.isShutdown
    _        <- if (shutdown) ZIO.fail(new RuntimeException("Actor stopped")) else queue.offer((Right(fa), promise))
  } yield ()

  override private[actors] val stop: Task[List[_]] = for {
    _          <- postStop()
    tail       <- queue.takeAll
    _          <- queue.shutdown
  } yield tail

  override def toString: String = s"ActorRef($path)"
}
