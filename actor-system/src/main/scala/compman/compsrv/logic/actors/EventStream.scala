package compman.compsrv.logic.actors

import zio.{Ref, Task, UIO}

import scala.reflect.ClassTag

case class EventStream(private val subscriptions: Ref[Map[Class[_], Set[ActorRef[Any]]]]) {
  import cats.implicits._
  import zio.interop.catz._
  def publish(msg: Any): Task[Unit] = for {
    subs <- subscriptions.get
    _    <- subs.get(msg.getClass).map(_.toList.traverse(_ ! msg)).getOrElse(UIO.unit)
  } yield ()
  def subscribe[Classifier: ClassTag](subscriber: ActorRef[Classifier]): Task[Unit] = {
    for {
      _ <- subscriptions.update(_.updatedWith(implicitly[ClassTag[Classifier]].runtimeClass)(p => {
        val unsafeSubscriber = subscriber.asInstanceOf[ActorRef[Any]]
        p.map(s => s + unsafeSubscriber).orElse(Option(Set(unsafeSubscriber)))
      }))
    } yield ()
  }
  def unsubscribe[Classifier: ClassTag](subscriber: ActorRef[Classifier]): Task[Unit] = {
    for {
      _ <- subscriptions.update(_.updatedWith(implicitly[ClassTag[Classifier]].runtimeClass)(p => {
        val unsafeSubscriber = subscriber.asInstanceOf[ActorRef[Any]]
        p.map(s => s - unsafeSubscriber)
      }))
    } yield ()
  }
}
