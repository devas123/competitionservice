package compman.compsrv.logic.actors

import compman.compsrv.logic.{Operations, StateOperations}
import compman.compsrv.logic.actors.CompetitionProcessor.Context
import compman.compsrv.logic.StateOperations.GetStateConfig
import compman.compsrv.logic.actors.Messages._
import compman.compsrv.model.{CompetitionState, Errors}
import compman.compsrv.model.events.EventDTO
import zio.{Fiber, Promise, Queue, Ref, Task}

import java.util.concurrent.TimeUnit

final class CompetitionProcessor {
  import compman.compsrv.Main.Live._
  import zio.interop.catz._
  type PendingMessage[A] = (Message, zio.Promise[Errors.Error, A])
  private val DefaultTimerKey      = "stopTimer"
  private val DefaultTimerDuration = zio.duration.Duration(5, TimeUnit.MINUTES)
  private def receive(
      context: Context,
      state: CompetitionState,
      command: Message,
      timers: Timers
  ): Task[Either[Errors.Error, Seq[EventDTO]]] = {
    for {
      self <- context.self
      res <-
        command match {
          case ProcessCommand(fa) =>
            timers.startDestroyTimer(DefaultTimerKey, DefaultTimerDuration) *>
              Operations.processCommand[Task](state, fa)
          case Stop =>
            self.stop.as(Right(Seq.empty))
        }
    } yield res
  }

  private def applyEvent(state: CompetitionState, eventDTO: EventDTO): Task[CompetitionState] = Operations
    .applyEvent[Task](state, eventDTO)


  private def makeActor(
                         id: String,
                         getStateConfig: GetStateConfig,
                         processorConfig: CommandProcessorOperations,
                         context: Context,
                         mailboxSize: Int
  )(postStop: () => Task[Unit]): Task[CompetitionProcessorActorRef] = {
    def process(
        msg: PendingMessage[Seq[EventDTO]],
        state: Ref[CompetitionState],
        ts: Timers
    ): Task[Unit] = {
      for {
        s <- state.get
        (command, promise) = msg
        receiver <- receive(context, s, command, ts)
        effectfulCompleter =
          (s: CompetitionState, a: Seq[EventDTO]) => state.set(s) *> promise.succeed(a)
        idempotentCompleter = (a: Seq[EventDTO]) => promise.succeed(a)
        fullCompleter =
          (
              (
                  ev: Command[EventDTO],
                  sa: CompetitionState => Seq[EventDTO]
              ) =>
                ev match {
                  case Command.Ignore =>
                    idempotentCompleter(sa(s))
                  case Command.Persist(ev) =>
                    for {
                      _            <- processorConfig.persistEvents(ev)
                      updatedState <- ev.foldLeft(Task(s))((a, b) => a.flatMap(applyEvent(_, b)))
                      res          <- effectfulCompleter(updatedState, sa(updatedState))
                    } yield res
                }
          ).tupled
        _ <- receiver
          .fold(promise.fail, events => fullCompleter(Command.Persist(events), _ => events))
      } yield ()
    }

    for {
      config       <- StateOperations.createConfig(getStateConfig)
      statePromise <- Promise.make[Throwable, Ref[CompetitionState]]
      _ <-
        (
          for {
            initial <- processorConfig.getLatestState(config)
            events  <- processorConfig.retrieveEvents(id)
            updated <- events.foldLeft(Task(initial))((a, b) => a.flatMap(applyEvent(_, b)))
            s       <- Ref.make(updated)
            _       <- statePromise.succeed(s)
          } yield ()
        ).fork
      queue <- Queue.sliding[PendingMessage[Seq[EventDTO]]](mailboxSize)
      actor = CompetitionProcessorActorRef(queue)(postStop)
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      ts = Timers(actor, timersMap, processorConfig)
      _ <-
        (
          for {
            state <- statePromise.await
            loop <-
              (
                for {
                  t <- queue.take
                  _ <- process(t, state, ts)
                } yield ()
              ).forever.fork
            _ <- loop.await
          } yield ()
        ).fork
    } yield actor
  }
}

object CompetitionProcessor {
  case class Context(actorsMapRef: Ref[Map[String, CompetitionProcessorActorRef]], id: String) {
    def self: Task[CompetitionProcessorActorRef] =
      for {
        map <- actorsMapRef.get
      } yield map(id)
  }

  private val DefaultActorMailboxSize: Int = 100

  def apply(
             id: String,
             getStateConfig: GetStateConfig,
             processorConfig: CommandProcessorOperations,
             context: Context,
             mailboxSize: Int = DefaultActorMailboxSize
  )(postStop: () => Task[Unit]): Task[CompetitionProcessorActorRef] =
    new CompetitionProcessor()
      .makeActor(id, getStateConfig, processorConfig, context, mailboxSize)(postStop)

}
