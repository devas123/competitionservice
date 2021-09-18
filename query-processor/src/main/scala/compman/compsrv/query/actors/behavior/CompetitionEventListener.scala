package compman.compsrv.query.actors.behavior

import compman.compsrv.model.events.EventDTO
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import zio.{Fiber, RIO, Tag}

object CompetitionEventListener {
  sealed trait ApiCommand[+_]
  case class EventReceived(event: EventDTO) extends ApiCommand[Unit]

  case class ActorState()
  val initialState: ActorState = ActorState()
  def behavior[R: Tag](eventStreaming: EventStreaming[R], topic: String): ActorBehavior[R, ActorState, ApiCommand] =
    new ActorBehavior[R, ActorState, ApiCommand] {
      override def receive[A](
        context: Context[ApiCommand],
        actorConfig: ActorConfig,
        state: ActorState,
        command: ApiCommand[A],
        timers: Timers[R, ApiCommand]
      ): RIO[R, (ActorState, A)] = ???

      override def init(
        actorConfig: ActorConfig,
        context: Context[ApiCommand],
        initState: ActorState,
        timers: Timers[R, ApiCommand]
      ): RIO[R, (Seq[Fiber[Throwable, Unit]], Seq[ApiCommand[Any]])] = super
        .init(actorConfig, context, initState, timers)
    }
}
