package compman.compsrv.logic

import compman.compsrv.jackson.SerdeApi
import compman.compsrv.logic.StateOperations.GetStateConfig
import compman.compsrv.model.{CompetitionState, Errors}
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.CompetitionStateCrudRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.rocksdb.RocksDB
import zio.{Chunk, Has, Layer, Promise, Queue, Ref, Task, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde

final case class CompetitionProcessingActor(queue: Queue[(CommandDTO, zio.Promise[Errors.Error, Seq[EventDTO]])])(
    postStop: () => Task[Unit]
) {
  def !(fa: CommandDTO): Task[Unit] =
    for {
      promise <- Promise.make[Errors.Error, Seq[EventDTO]]
      _       <- queue.offer((fa, promise))
    } yield ()

  val stop: Task[List[_]] =
    for {
      tall <- queue.takeAll
      _    <- queue.shutdown
      _    <- postStop()
    } yield tall
}

sealed trait Command[+Ev]
object Command {
  case class Persist[+Ev](event: Seq[Ev]) extends Command[Ev]
  case object Ignore                      extends Command[Nothing]

  def persist[Ev](event: Seq[Ev]): Persist[Ev] = Persist(event)
  def ignore: Ignore.type                      = Ignore
}

final case class CompetitionProcessor() {
  import compman.compsrv.Main.Live._
  import zio.interop.catz._
  type PendingMessage[A] = (CommandDTO, zio.Promise[Errors.Error, A])
  val DefaultActorMailboxSize: Int = 100
  def receive(
      state: CompetitionState,
      command: CommandDTO): Task[Either[Errors.Error, Seq[EventDTO]]] = Operations.processCommand[Task](state, command)

  def applyEvent(state: CompetitionState, eventDTO: EventDTO): Task[CompetitionState] = Operations
    .applyEvent[Task](state, eventDTO)

  def retreiveEvents(id: String, context: Context): Task[List[EventDTO]] = {
    Consumer.subscribeAnd(Subscription.topics(id)).plainStream(Serde.string, SerdeApi.eventDeserializer)
      .runCollect.map(_.map(_.value).toList).provideSomeLayer(context.kafkaConsumerLayer).provideLayer(context.clockLayer ++ context.blockingLayer)
  }

  def persistEvents(events: Seq[EventDTO], context: Context): Task[Unit] = {
    zio.kafka.producer.Producer.produceChunk[Any, String, EventDTO](Chunk.fromIterable(events).map(e => new ProducerRecord[String, EventDTO](e.getCompetitionId, e)))
      .provideLayer(context.kafkaProducerLayer ++ context.blockingLayer).ignore
  }

  def makeActor(
      id: String,
      getStateConfig: GetStateConfig,
      context: Context,
      mailboxSize: Int = DefaultActorMailboxSize
  ): Task[CompetitionProcessingActor] = {
    def process(msg: PendingMessage[Seq[EventDTO]], state: Ref[CompetitionState]): Task[Unit] =
      for {
        s <- state.get
        (command, promise) = msg
        receiver <- receive(s, command)
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
                      _ <- persistEvents(ev, context)
                      updatedState <-
                        ev.foldLeft(Task(s))((a, b) => a.flatMap(applyEvent(_, b)))
                      res <- effectfulCompleter(updatedState, sa(updatedState))
                    } yield res
                }
          ).tupled
        _ <- receiver.fold(promise.fail, events => fullCompleter(Command.Persist(events), _ => events))
      } yield ()
    for {
      rdb     <- Ref.make(CompetitionStateCrudRepository.createLive(RocksDB.open(id)))
      db      <- rdb.get
      config  <- StateOperations.createConfig(getStateConfig)
      events  <- retreiveEvents(id, context)
      initial <- StateOperations.getLatestState(config, db)
      updated <- events.foldLeft(Task(initial))((a, b) => a.flatMap(applyEvent(_, b)))
      state   <- Ref.make(updated)
      queue   <- Queue.bounded[PendingMessage[Seq[EventDTO]]](mailboxSize)
      _ <-
        (
          for {
            t <- queue.take
            _ <- process(t, state)
          } yield ()
        ).forever.fork
    } yield CompetitionProcessingActor(queue)(() => db.close())
  }

}

trait Context {
  def kafkaConsumerLayer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]]
  def kafkaProducerLayer: ZLayer[Any, Throwable, Has[Producer.Service[Any, String, EventDTO]]]
  def clockLayer: Layer[Nothing, Clock]
  def blockingLayer: Layer[Nothing, Blocking]
}
