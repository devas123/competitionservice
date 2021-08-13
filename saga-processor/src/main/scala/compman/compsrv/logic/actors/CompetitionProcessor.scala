package compman.compsrv.logic.actors

import compman.compsrv.jackson.SerdeApi
import compman.compsrv.logic.{Operations, StateOperations}
import compman.compsrv.logic.actors.CompetitionProcessor.Context
import compman.compsrv.logic.StateOperations.GetStateConfig
import compman.compsrv.model.{CompetitionState, Errors}
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Chunk, Fiber, Has, Layer, Promise, Queue, Ref, Task, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde

import java.util.concurrent.TimeUnit

sealed trait Message
final case class ProcessCommand(fa: CommandDTO) extends Message
object Stop                                     extends Message

final case class CompetitionProcessorActorRef(
    private val queue: Queue[(Message, zio.Promise[Errors.Error, Seq[EventDTO]])]
)(private val postStop: () => Task[Unit]) {
  def !(fa: Message): Task[Unit] =
    for {
      promise <- Promise.make[Errors.Error, Seq[EventDTO]]
      _       <- queue.offer((fa, promise))
    } yield ()

  private[actors] val stop: Task[List[_]] =
    for {
      tall <- queue.takeAll
      _    <- queue.shutdown
      _    <- postStop()
    } yield tall
}

private[actors] sealed trait Command[+Ev]
private[actors] object Command {
  case class Persist[+Ev](event: Seq[Ev]) extends Command[Ev]
  case object Ignore                      extends Command[Nothing]

  def persist[Ev](event: Seq[Ev]): Persist[Ev] = Persist(event)
  def ignore: Ignore.type                      = Ignore
}

final case class CompetitionProcessor() {
  import compman.compsrv.Main.Live._
  import zio.interop.catz._
  type PendingMessage[A] = (Message, zio.Promise[Errors.Error, A])
  private val DefaultActorMailboxSize: Int = 100
  private val DefaultTimerKey              = "stopTimer"
  private val DefaultTimerDuration         = zio.duration.Duration(5, TimeUnit.MINUTES)
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

  def makeActor(
      id: String,
      getStateConfig: GetStateConfig,
      processorConfig: CommandProcessorConfig,
      context: Context,
      mailboxSize: Int = DefaultActorMailboxSize
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
      config  <- StateOperations.createConfig(getStateConfig)
      events  <- retreiveEvents(id, processorConfig)
      initial <- StateOperations.getLatestState(config)
      updated <- events.foldLeft(Task(initial))((a, b) => a.flatMap(applyEvent(_, b)))
      state   <- Ref.make(updated)
      queue   <- Queue.bounded[PendingMessage[Seq[EventDTO]]](mailboxSize)
      actor = CompetitionProcessorActorRef(queue)(postStop)
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      ts = Timers(actor, timersMap, processorConfig)
      _ <-
        (
          for {
            t <- queue.take
            _ <- process(t, state, ts)
          } yield ()
        ).forever.fork
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
}

trait CommandProcessorConfig {
  def kafkaConsumerLayer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]]
  def kafkaProducerLayer: ZLayer[Any, Throwable, Has[Producer.Service[Any, String, EventDTO]]]
  def clockLayer: Layer[Nothing, Clock]
  def blockingLayer: Layer[Nothing, Blocking]
}
