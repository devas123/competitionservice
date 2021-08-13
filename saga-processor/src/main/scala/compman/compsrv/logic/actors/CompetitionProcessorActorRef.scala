package compman.compsrv.logic.actors

import compman.compsrv.model.Errors
import compman.compsrv.model.events.EventDTO
import zio.{Promise, Queue, Task}

import Messages._

final case class CompetitionProcessorActorRef(
    private val queue: Queue[(Message, zio.Promise[Errors.Error, Seq[EventDTO]])]
)(private val postStop: () => Task[Unit]) {
  def !(fa: Message): Task[Unit] =
    for {
      promise <- Promise.make[Errors.Error, Seq[EventDTO]]
      _       <- queue.offer((fa, promise))
    } yield ()

  private[actors] val stop: Task[List[_]] =
    for {
      tall <- queue.takeAll
      _    <- queue.shutdown
      _    <- postStop()
    } yield tall
}
