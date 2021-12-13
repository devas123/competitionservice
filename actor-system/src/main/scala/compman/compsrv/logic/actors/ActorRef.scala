package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.PendingMessage
import compman.compsrv.logic.actors.dungeon.{DeadLetter, SystemMessage}
import zio.{Queue, Task}

import scala.annotation.unchecked.uncheckedVariance

trait ActorRef[-Msg] {
  private[actors] def sendSystemMessage(msg: SystemMessage): Task[Unit]

  def !(fa: Msg): Task[Unit]

  private[actors] val stop: Task[List[_]]

  private[actors] def unsafeUpcast[T >: Msg @uncheckedVariance] = this.asInstanceOf[ActorRef[T]]
  private[actors] def narrow[T <: Msg] = this.asInstanceOf[ActorRef[T]]
}

private[actors] trait MinimalActorRef[Msg] extends ActorRef[Msg] {
  override private[actors] def sendSystemMessage(msg: SystemMessage) = Task.unit
  override def !(fa: Msg): Task[Unit] = Task.unit
  override private[actors] val stop = Task(List.empty)
}

private[actors] case class LocalActorRef[Msg](private val queue: Queue[PendingMessage[Msg]], private val path: ActorPath)(
  private val postStop: () => Task[Unit],
  private val provider: ActorRefProvider
) extends ActorRef[Msg] {

  override def hashCode(): Int = path.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case x: LocalActorRef[_] => path.uid == x.path.uid && path == x.path
    case _              => false
  }

  override private[actors] def sendSystemMessage(systemMessage: SystemMessage): Task[Unit] = for {
    shutdown <- queue.isShutdown
    _ <- if (shutdown) provider.deadLetters ! DeadLetter(systemMessage, None, this.narrow[Nothing]) else queue.offer(Left(systemMessage))
  } yield ()

  override def !(message: Msg): Task[Unit] = for {
    shutdown <- queue.isShutdown
    _ <- if (shutdown) provider.deadLetters ! DeadLetter(message, None, this.narrow[Nothing]) else queue.offer(Right(message))
  } yield ()

  override private[actors] val stop: Task[List[_]] = for {
    _          <- postStop()
    tail       <- queue.takeAll
    _          <- queue.shutdown
  } yield tail

  override def toString: String = s"ActorRef($path)"
}

private[actors] case class DeadLetterActorRef(eventStream: EventStream) extends MinimalActorRef[Any] {
  override def !(fa: Any): Task[Unit] = {
    fa match {
      case d: DeadLetter => eventStream.publish(d)
      case _ =>
        eventStream.publish(DeadLetter(fa, None, this))
    }
  }
}