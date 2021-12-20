package compman.compsrv.logic.actors

import zio.{Chunk, Fiber, RIO, Ref, Supervisor, URIO}
import zio.clock.Clock
import zio.duration.Duration

case class Timers[R, Msg](
                           private val self: ActorRef[Msg],
                           private val timers: Ref[Map[String, Fiber[Throwable, Unit]]],
                           private val supervisor: Supervisor[Chunk[Fiber.Runtime[Any, Any]]]
) {
  def startSingleTimer(key: String, delay: Duration, msg: Msg): RIO[R with Clock, Unit] = {
    def create = (RIO.sleep(delay) <* (self ! msg)).supervised(supervisor).forkDaemon
    updateTimers(key, create)
  }
  def startRepeatedTimer(
    key: String,
    initialDelay: Duration,
    interval: Duration,
    msg: Msg
  ): RIO[R with Clock, Unit] = {
    def create = (RIO.sleep(initialDelay) <* (RIO.sleep(interval) <* (self ! msg)).forever.supervised(supervisor).forkDaemon).supervised(supervisor).forkDaemon
    updateTimers(key, create)
  }

  private def updateTimers(key: String, create: URIO[Clock, Fiber.Runtime[Throwable, Unit]]) = {
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

  def cancelTimer(key: String): RIO[R, Unit] = {
    for {
      fiber <- timers.modify(ts => {
        val fiber = ts.get(key)
        val nm    = ts - key
        (fiber.map(_.interrupt), nm)
      })
      _ <- fiber.getOrElse(RIO.unit)
    } yield ()
  }

  def cancelAll(): RIO[R, Unit] = {
    import cats.implicits._
    import zio.interop.catz._
    for {
      timersMap <- timers.get
      _ <- timersMap.values.toList.traverse { fiber => fiber.interruptFork.disconnect }
      _ <- timers.set(Map.empty)
    } yield ()
  }
}
