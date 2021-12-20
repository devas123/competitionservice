package compman.compsrv.logic.actors
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.DeadLetter
import zio.RIO
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

case class DeadLetterListener() extends MinimalBehavior[Logging, Int, DeadLetter] {
  override def receive(
    context: Context[DeadLetter],
    actorConfig: ActorSystem.ActorConfig,
    state: Int,
    command: DeadLetter,
    timers: Timers[Logging, DeadLetter]
  ): RIO[Logging, Int] = command match {
    case DeadLetter(message, sender, receiver) => Logging.warn(
        s"Message: $message from ${sender.getOrElse("")} to $receiver was not delivered. ${state + 1} dead letters received."
      ).as((state + 1) % Int.MaxValue)
  }
}

object DeadLetterListener {
  def apply(actorSystem: ActorSystem): RIO[Logging with Clock with Console, ActorRef[DeadLetter]] = {
    actorSystem.make("DeadLetters", ActorConfig(), 0, DeadLetterListener())
  }
}
