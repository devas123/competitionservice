package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.Messages._
import zio.{Fiber, Ref, Task, UIO, ZIO}
import zio.duration.Duration

private[actors] case class Timers(
    private val self: CompetitionProcessorActorRef,
    private val timers: Ref[Map[String, Fiber[Throwable, Unit]]],
    private val processorConfig: CommandProcessorConfig
) {
  def startDestroyTimer[A](key: String, timeout: Duration): Task[Unit] = {
    def create = (ZIO.sleep(timeout) <* (self ! Stop)).fork.provideLayer(processorConfig.clockLayer)
    for {
      map <- timers.get
      maybeTimer = map.get(key)
      fiber <-
        maybeTimer match {
          case Some(value) =>
            for {
              _ <- value.interrupt
              f <- create
            } yield f
          case None =>
            create
        }
      _ <- timers.update(map => map + (key -> fiber))
    } yield ()
  }
  def cancelTimer[A](key: String): UIO[Unit] = {
    for {
      fiber <- timers.modify(ts => {
        val fiber = ts.get(key)
        val nm    = ts - key
        (fiber.map(_.interruptFork), nm)
      })
      res <- fiber.getOrElse(Task.unit)
    } yield res
  }
}
