package compman.compsrv.query.actors.behavior

import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model.CompetitionProperties._
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import zio.RIO

object CompetitionApiActor {
  sealed trait ApiCommand[+_]
  final case object GetCompetitionInfoTemplate extends ApiCommand[CompetitionInfoTemplate]
  case class ActorState()
  val initialState: ActorState = ActorState()
  def behavior[R](
    eventStreaming: EventStreaming[R],
    topic: String
    ): ActorBehavior[R, ActorState, ApiCommand] = new ActorBehavior[R, ActorState, ApiCommand] {
    override def receive[A](
      context: Context[ApiCommand],
      actorConfig: ActorConfig,
      state: ActorState,
      command: ApiCommand[A],
      timers: Timers[R, ApiCommand]
    ): RIO[R, (ActorState, A)] = ???
  }
}
