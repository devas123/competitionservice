package compman.compsrv.query.actors

import zio.RIO

object CompetitionEventListenerSupervisor {
  sealed trait ActorMessages[+_]
  case class DefaultMsg() extends ActorMessages[Unit]
  val behavior: ActorBehavior[Any, Unit, ActorMessages] = new ActorBehavior[Any, Unit, ActorMessages] {
    override def receive[A](
      context: CompetitionProcessorActor.Context,
      actorConfig: CompetitionApiActor.ActorConfig,
      state: Unit,
      command: ActorMessages[A],
      timers: Timers[Any, ActorMessages]
    ): RIO[Any, (Unit, A)] = ???

    override def init(
      context: CompetitionProcessorActor.Context,
      actorConfig: CompetitionApiActor.ActorConfig,
      initState: Unit,
      timers: Timers[Any, ActorMessages]
    ): RIO[Any, Unit] = ???
  }
}
