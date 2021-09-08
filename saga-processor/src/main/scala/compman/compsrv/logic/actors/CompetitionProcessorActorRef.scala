package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.Messages._
import compman.compsrv.model.Errors
import compman.compsrv.model.events.EventDTO
import zio.{Promise, Queue, RIO, Task}

final case class CompetitionProcessorActorRef[Env](
    private val queue: Queue[(Message, zio.Promise[Errors.Error, Seq[EventDTO]])]
)(private val postStop: () => RIO[Env, Unit]) {
  def !(fa: Message): Task[Unit] =
    for {
      promise <- Promise.make[Errors.Error, Seq[EventDTO]]
      _       <- queue.offer((fa, promise))
    } yield ()

  private[actors] val stop: RIO[Env, List[_]] =
    for {
      tall <- queue.takeAll
      _    <- queue.shutdown
      _    <- postStop()
    } yield tall
}
