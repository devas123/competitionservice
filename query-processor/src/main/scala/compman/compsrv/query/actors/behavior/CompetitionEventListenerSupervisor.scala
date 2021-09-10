package compman.compsrv.query.actors.behavior

import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.service.repository.ManagedCompetitions
import zio.RIO

object CompetitionEventListenerSupervisor {
  sealed trait ActorMessages[+_]
  case class DefaultMsg() extends ActorMessages[Unit]
  val behavior: ActorBehavior[ManagedCompetitions.Service, Unit, ActorMessages] =
    new ActorBehavior[ManagedCompetitions.Service, Unit, ActorMessages] {
      override def receive[A](
        context: Context[ActorMessages],
        actorConfig: ActorConfig,
        state: Unit,
        command: ActorMessages[A],
        timers: Timers[ManagedCompetitions.Service, ActorMessages]
      ): RIO[ManagedCompetitions.Service, (Unit, A)] = ???
    }
}
