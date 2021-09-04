package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.Messages._
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.Errors
import compman.compsrv.model.events.EventDTO
import zio.{Promise, Queue}

final case class CompetitionProcessorActorRef(
    private val queue: Queue[(Message, zio.Promise[Errors.Error, Seq[EventDTO]])]
)(private val postStop: () => LIO[Unit]) {
  def !(fa: Message): LIO[Unit] =
    for {
      promise <- Promise.make[Errors.Error, Seq[EventDTO]]
      _       <- queue.offer((fa, promise))
    } yield ()

  private[actors] val stop: LIO[List[_]] =
    for {
      tall <- queue.takeAll
      _    <- queue.shutdown
      _    <- postStop()
    } yield tall
}
