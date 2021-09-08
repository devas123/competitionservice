package compman.compsrv.logic.actors

import compman.compsrv.logic.Operations
import compman.compsrv.logic.actors.CompetitionProcessorActor.Context
import compman.compsrv.logic.actors.Messages._
import compman.compsrv.logic.logging.CompetitionLogging.{Annotations, LIO, Live}
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.{CompetitionState, Errors}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.Consumer
import zio.kafka.producer.Producer
import zio.logging.{LogAnnotation, Logging}
import zio.{Fiber, Promise, Queue, RIO, Ref, Task, ZIO}

import java.util.UUID
import java.util.concurrent.TimeUnit

final class CompetitionProcessorActor {
  import compman.compsrv.Main.Live._
  import compman.compsrv.logic.logging._
  import zio.interop.catz._
  type PendingMessage[A] = (Message, zio.Promise[Errors.Error, A])
  private val DefaultTimerKey      = "stopTimer"
  private val DefaultTimerDuration = zio.duration.Duration(5, TimeUnit.MINUTES)
  private def receive[Env](
    context: Context[Env],
    state: CompetitionState,
    command: Message,
    timers: Timers[Env]
  ): RIO[Env with Logging with Clock, Either[Errors.Error, Seq[EventDTO]]] = {
    for {
      self <- context.self
      _    <- info(s"Received a command $command")
      res <- command match {
        case ProcessCommand(cmd) => timers.startDestroyTimer(DefaultTimerKey, DefaultTimerDuration) *> {
            for {
              _ <-
                if (cmd.getId == null) RIO.fail(new IllegalArgumentException(s"Command $cmd has no ID")) else RIO.unit
              res <- Live.withContext(
                _.annotate(LogAnnotation.CorrelationId, Option(cmd.getId).map(UUID.fromString))
                  .annotate(Annotations.competitionId, Option(cmd.getCompetitionId))
              ) { Operations.processCommand[LIO](state, cmd) }
            } yield res
          }
        case Stop => self.stop.as(Right(Seq.empty))
      }
    } yield res
  }

  private def applyEvent(state: CompetitionState, eventDTO: EventDTO): LIO[CompetitionState] = Live.withContext(
    _.annotate(LogAnnotation.CorrelationId, Option(UUID.fromString(eventDTO.getCorrelationId)))
      .annotate(Annotations.competitionId, Option(eventDTO.getCompetitionId))
  ) { Operations.applyEvent[LIO](state, eventDTO) }

  private def makeActor[Env](
                              actorConfig: ActorConfig,
                              processorOperations: CommandProcessorOperations[Env with Logging with Clock with Blocking],
                              context: Context[Env],
                              mailboxSize: Int
                            )(postStop: () => RIO[Env, Unit]): RIO[Env with Logging with Clock with Blocking with SnapshotService.Snapshot,
    CompetitionProcessorActorRef[Env]] = {
    def process(
                 msg: PendingMessage[Seq[EventDTO]],
                 stateRef: Ref[CompetitionState],
                 ts: Timers[Env]
               ): RIO[Env with Logging with Clock with Blocking with SnapshotService.Snapshot, Unit] = {
      for {
        state <- stateRef.get
        (command, promise) = msg
        receiver <- receive(context, state, command, ts)
        effectfulCompleter = (s: CompetitionState, a: Seq[EventDTO]) => stateRef.set(s) *> {
          if (s.revision % 10 == 0 || a.size >= 10) processorOperations.saveStateSnapshot(s) else ZIO.effect(())
        } *> promise.succeed(a)
        idempotentCompleter = (a: Seq[EventDTO]) => promise.succeed(a)
        fullCompleter = (
          (
            ev: Command[EventDTO],
            sa: CompetitionState => Seq[EventDTO]
          ) =>
            ev match {
              case Command.Ignore => idempotentCompleter(sa(state))
              case Command.Persist(ev) => for {
                  _ <- processorOperations.persistEvents(ev)
                  updatedState <- ev
                    .foldLeft[LIO[CompetitionState]](RIO.apply(state))((a, b) => a.flatMap(applyEvent(_, b)))
                  res <- effectfulCompleter(updatedState, sa(updatedState))
                } yield res
            }
        ).tupled
        _ <- receiver.fold(promise.fail, events => fullCompleter(Command.Persist(events), _ => events))
      } yield ()
    }

    for {
      statePromise <- Promise.make[Throwable, Ref[CompetitionState]]
      _ <- (for {
        latest <- processorOperations.getStateSnapshot(actorConfig.id) >>= (_.map(Task(_)).getOrElse(processorOperations.createInitialState(actorConfig)))
        events <- processorOperations.retrieveEvents(actorConfig.eventTopic, latest.revision)
        updated <- events.foldLeft[LIO[CompetitionState]](RIO(latest))((a, b) => a.flatMap(applyEvent(_, b)))
        s <- Ref.make(updated)
        _ <- statePromise.succeed(s)
      } yield ()).fork
      queue <- Queue.sliding[PendingMessage[Seq[EventDTO]]](mailboxSize)
      actor = CompetitionProcessorActorRef[Env](queue)(postStop)
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      ts = Timers[Env](actor, timersMap, processorOperations)
      _ <- (for {
        state <- statePromise.await
        _ <- processorOperations.sendNotifications(Seq(CompetitionProcessingStarted(actorConfig.id)))
        loop <- (for {
          t <- queue.take
          _ <- process(t, state, ts)
        } yield ()).forever.fork
        _ <- loop.await
      } yield ()).fork
    } yield actor
  }
}

object CompetitionProcessorActor {
  type LiveEnv = Logging with Clock with Blocking with Consumer with Producer[Any, String, Array[Byte]] with SnapshotService.Snapshot

  case class Context[Env](actorsMapRef: Ref[Map[String, CompetitionProcessorActorRef[Env]]], id: String) {
    def self: Task[CompetitionProcessorActorRef[Env]] = for { map <- actorsMapRef.get } yield map(id)
  }

  private val DefaultActorMailboxSize: Int = 100

  def apply[Env](
                  actorConfig: ActorConfig,
                  processorConfig: CommandProcessorOperations[Env with Logging with Clock with Blocking],
                  context: Context[Env],
                  mailboxSize: Int = DefaultActorMailboxSize
                )(postStop: () => RIO[Env, Unit]): RIO[Env with Logging with Clock with Blocking with SnapshotService.Snapshot,
    CompetitionProcessorActorRef[Env]] = new CompetitionProcessorActor()
    .makeActor(actorConfig, processorConfig, context, mailboxSize)(postStop)

}