package compman.compsrv.query.actors.behavior

import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model.CompetitionInfoTemplate
import zio.RIO

object CompetitionApiActor {
  sealed trait ApiCommand[+_]
  final case object GetCompetitionInfoTemplate extends ApiCommand[CompetitionInfoTemplate]
  case class ActorState()
  val initialState: ActorState = ActorState()
  val behavior: ActorBehavior[Any, ActorState, ApiCommand] = new ActorBehavior[Any, ActorState, ApiCommand] {
    override def receive[A](
      context: Context[ApiCommand],
      actorConfig: ActorConfig,
      state: ActorState,
      command: ApiCommand[A],
      timers: Timers[Any, ApiCommand]
    ): RIO[Any, (ActorState, A)] = ???
  }
}
