package compman.compsrv.logic.actors

import compman.compsrv.jackson.SerdeApi
import compman.compsrv.logic.{Operations, StateOperations}
import compman.compsrv.logic.actors.CompetitionProcessor.Context
import compman.compsrv.logic.StateOperations.GetStateConfig
import compman.compsrv.logic.actors.Messages._
import compman.compsrv.model.{CompetitionState, Errors}
import compman.compsrv.model.events.EventDTO
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Chunk, Fiber, Promise, Queue, Ref, Task}
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.serde.Serde

import java.util.concurrent.TimeUnit

final class CompetitionProcessor {
  import compman.compsrv.Main.Live._
  import zio.interop.catz._
  type PendingMessage[A] = (Message, zio.Promise[Errors.Error, A])
  private val DefaultTimerKey      = "stopTimer"
  private val DefaultTimerDuration = zio.duration.Duration(5, TimeUnit.MINUTES)
  def receive(
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

  def applyEvent(state: CompetitionState, eventDTO: EventDTO): Task[CompetitionState] = Operations
    .applyEvent[Task](state, eventDTO)

  def retreiveEvents(id: String, context: CommandProcessorConfig): Task[List[EventDTO]] = {
    Consumer
      .subscribeAnd(Subscription.topics(id))
      .plainStream(Serde.string, SerdeApi.eventDeserializer)
      .runCollect
      .map(_.map(_.value).toList)
      .provideSomeLayer(context.kafkaConsumerLayer)
      .provideLayer(context.clockLayer ++ context.blockingLayer)
  }

  def persistEvents(events: Seq[EventDTO], context: CommandProcessorConfig): Task[Unit] = {
    zio
      .kafka
      .producer
      .Producer
      .produceChunk[Any, String, EventDTO](
        Chunk
          .fromIterable(events)
          .map(e => new ProducerRecord[String, EventDTO](e.getCompetitionId, e))
      )
      .provideLayer(context.kafkaProducerLayer ++ context.blockingLayer)
      .ignore
  }

  private def makeActor(
      id: String,
      getStateConfig: GetStateConfig,
      processorConfig: CommandProcessorConfig,
      context: Context,
      mailboxSize: Int
  )(postStop: () => Task[Unit]): Task[CompetitionProcessorActorRef] = {
    def process(
        msg: PendingMessage[Seq[EventDTO]],
        state: Ref[CompetitionState],
        ts: Timers
    ): Task[Unit] =
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
                      _            <- persistEvents(ev, processorConfig)
                      updatedState <- ev.foldLeft(Task(s))((a, b) => a.flatMap(applyEvent(_, b)))
                      res          <- effectfulCompleter(updatedState, sa(updatedState))
                    } yield res
                }
          ).tupled
        _ <- receiver
          .fold(promise.fail, events => fullCompleter(Command.Persist(events), _ => events))
      } yield ()
    for {
      config       <- StateOperations.createConfig(getStateConfig)
      statePromise <- Promise.make[Throwable, Ref[CompetitionState]]
      _ <-
        (
          for {
            events  <- retreiveEvents(id, processorConfig)
            initial <- StateOperations.getLatestState(config)
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
  case class Context(actors: Ref[Map[String, CompetitionProcessorActorRef]], id: String) {
    def self: Task[CompetitionProcessorActorRef] =
      for {
        map <- actors.get
      } yield map(id)
  }

  private val DefaultActorMailboxSize: Int = 100

  def apply(
      id: String,
      getStateConfig: GetStateConfig,
      processorConfig: CommandProcessorConfig,
      context: Context,
      mailboxSize: Int = DefaultActorMailboxSize
  )(postStop: () => Task[Unit]): Task[CompetitionProcessorActorRef] =
    new CompetitionProcessor()
      .makeActor(id, getStateConfig, processorConfig, context, mailboxSize)(postStop)

}
