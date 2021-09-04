package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.Messages._
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import zio.{Fiber, Ref, RIO}
import zio.clock.Clock
import zio.duration.Duration
import zio.logging.Logging

private[actors] case class Timers[Env](
  private val self: CompetitionProcessorActorRef,
  private val timers: Ref[Map[String, Fiber[Throwable, Unit]]],
  private val processorConfig: CommandProcessorOperations[Env]
) {
  def startDestroyTimer[A](key: String, timeout: Duration): RIO[Env with Logging with Clock, Unit] = {
    def create = (RIO.sleep(timeout) <* (self ! Stop)).fork
    for {
      map <- timers.get
      maybeTimer = map.get(key)
      fiber <- maybeTimer match {
        case Some(value) => for {
            _ <- value.interrupt
            f <- create
          } yield f
        case None => create
      }
      _ <- timers.update(map => map + (key -> fiber))
    } yield ()
  }
  def cancelTimer[A](key: String): LIO[Unit] = {
    for {
      fiber <- timers.modify(ts => {
        val fiber = ts.get(key)
        val nm    = ts - key
        (fiber.map(_.interruptFork), nm)
      })
      res <- fiber.getOrElse(RIO.unit)
    } yield res
  }
}
