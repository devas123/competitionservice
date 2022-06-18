package compman.compsrv.logic.actors
import zio.{RIO, URIO}

class MinimalEventSourcedBehavior[R, S, Msg, Ev](persistenceId: String) extends EventSourcedBehavior[R, S, Msg, Ev](persistenceId: String) {
  override def receive(context: Context[Msg], actorConfig: ActorSystem.ActorConfig, state: S, command: Msg, timers: Timers[R, Msg]): RIO[R, (EventSourcedMessages.EventSourcingCommand[Ev], S => Unit)] =
    RIO.effectTotal((EventSourcedMessages.EventSourcingCommand.Ignore, _ => ()))

  override def sourceEvent(state: S, event: Ev): RIO[R, S] = RIO.effectTotal(state)

  override def getEvents(persistenceId: String, state: S): RIO[R, Seq[Ev]] = RIO.effectTotal(Seq.empty)

  override def persistEvents(persistenceId: String, events: Seq[Ev]): URIO[R, Unit] = URIO.unit
}
