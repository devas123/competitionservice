package compman.compsrv.logic.actors

import zio.{Fiber, Ref, RIO, URIO}
import zio.clock.Clock
import zio.duration.Duration

case class Timers[R, Msg[+_]](
  private val self: ActorRef[Msg],
  private val timers: Ref[Map[String, Fiber[Throwable, Unit]]]
) {
  def startSingleTimer[A](key: String, delay: Duration, msg: Msg[A]): RIO[R with Clock, Unit] = {
    def create = (RIO.sleep(delay) <* (self ! msg)).fork
    updateTimers(key, () => create)
  }
  def startRepeatedTimer[A](
    key: String,
    initialDelay: Duration,
    interval: Duration,
    msg: Msg[A]
  ): RIO[R with Clock, Unit] = {
    def create = (RIO.sleep(initialDelay) <* (RIO.sleep(interval) <* (self ! msg)).forever.fork).fork
    updateTimers(key, () => create)
  }

  private def updateTimers[A](key: String, create: () => URIO[Clock, Fiber.Runtime[Throwable, Unit]]) = {
    for {
      map <- timers.get
      maybeTimer = map.get(key)
      fiber <- maybeTimer match {
        case Some(value) => for {
            _ <- value.interrupt
            f <- create()
          } yield f
        case None => create()
      }
      _ <- timers.update(map => map + (key -> fiber))
    } yield ()
  }

  def cancelTimer[A](key: String): RIO[R, Unit] = {
    for {
      fiber <- timers.modify(ts => {
        val fiber = ts.get(key)
        val nm    = ts - key
        (fiber.map(_.interrupt), nm)
      })
      res <- fiber.getOrElse(RIO.unit)
    } yield ()
  }

  def cancelAll[A](): RIO[R, Unit] = {
    import cats.implicits._
    import zio.interop.catz._
    for {
      timersMap <- timers.get
      _         <- timersMap.values.toList.traverse { fiber => fiber.interrupt}
      _         <- timers.set(Map.empty)
    } yield ()
  }

}
