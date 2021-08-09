package compman.compsrv.logic

import cats.data.EitherT
import cats.Monad
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.ObjectMapperFactory
import compman.compsrv.logic.Mapping.{CommandMapping, EventMapping}
import compman.compsrv.model._
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.{
  CategoryDescriptorDTO,
  CompetitionPropertiesDTO,
  CompetitorDTO,
  FightDescriptionDTO
}
import compman.compsrv.model.events.{EventDTO, EventType}
import org.apache.kafka.clients.producer.ProducerRecord
import org.rocksdb.RocksDB
import zio.{IO, Ref, Task}
import zio.kafka.consumer.CommittableRecord

import java.util

object Operations {

  trait CrudOperations[F[+_], A, Context[_], DB] {
    def add(ctx: Context[DB], entity: A): F[Unit]
    def remove(ctx: Context[DB], id: String): F[Unit]
    def get(ctx: Context[DB], id: String): F[A]
    def exists(ctx: Context[DB], id: String): F[Boolean]
  }

  trait IdOperations[F[_]] {
    def createId(competitor: CompetitorDTO): F[String]
  }

  trait CommandEventOperations[F[+_], A, T] {
    def lift(obj: => Seq[A]): F[Seq[A]]
    def create[P <: Payload](
        `type`: EventType,
        competitionId: Option[String] = None,
        competitorId: Option[String] = None,
        fightId: Option[String] = None,
        categoryId: Option[String] = None,
        payload: Option[P]
    ): F[A]
    def error(error: => Errors.Error): F[Either[Errors.Error, A]]
  }
  trait EventOperations[F[+_]] extends CommandEventOperations[F, EventDTO, EventType]

  object EventOperations {
    val live: EventOperations[Task] =
      new EventOperations[Task] {
        override def lift(obj: => Seq[EventDTO]): Task[Seq[EventDTO]] = Task(obj)

        override def error(error: => Errors.Error): Task[Either[Errors.Error, EventDTO]] = Task {
          Left(error)
        }

        override def create[P <: Payload](
            `type`: EventType,
            competitionId: Option[String],
            competitorId: Option[String],
            fightId: Option[String],
            categoryId: Option[String],
            payload: Option[P]
        ): Task[EventDTO] = Task {
          val e = new EventDTO()
          e.setType(`type`)
          competitionId.foreach(e.setCompetitionId)
          competitorId.foreach(e.setCompetitorId)
          categoryId.foreach(e.setCategoryId)
          payload.foreach(e.setPayload)
          e
        }
      }

  }

  trait CompetitionCrudOperations[F[+_], Context[_], DB]
      extends CrudOperations[F, CompetitionPropertiesDTO, Context, DB]
  trait CategoryCrudOperations[F[+_], Context[_], DB]
      extends CrudOperations[F, CategoryDescriptorDTO, Context, DB]
  trait FightCrudOperations[F[+_], Context[_], DB]
      extends CrudOperations[F, FightDescriptionDTO, Context, DB]
  trait CompetitorCrudOperations[F[+_], Context[_], DB]
      extends CrudOperations[F, CompetitorDTO, Context, DB]
  trait CompetitionStateCrudOperations[F[+_], Context[_], DB]
      extends CrudOperations[F, CompetitionState, Context, DB]

  trait Holder[F[_], A[_], V] {
    def create(a: => A[V]): F[A[V]]
  }

  object Holder {
    def apply[F[_], A[_], V](implicit F: Holder[F, A, V]): Holder[F, A, V] = F

    val live: Holder[Task, Ref, RocksDB] = (a: Ref[RocksDB]) => IO(a)
  }

  object CompetitionStateCrudOperations {
    val live: CompetitionStateCrudOperations[Task, Ref, RocksDB] =
      new CompetitionStateCrudOperations[Task, Ref, RocksDB] {
        val objectMapper: ObjectMapper = ObjectMapperFactory.createObjectMapper
        override def add(holder: Ref[RocksDB], entity: CompetitionState): Task[Unit] = {
          for {
            rdb <- holder.get
            _ <- IO {
              rdb.put(entity.id.getBytes, objectMapper.writeValueAsBytes(entity))
            }
          } yield ()
        }

        override def remove(ctx: Ref[RocksDB], id: String): Task[Unit] =
          for {
            rdb <- ctx.get
            _ <- IO {
              rdb.delete(id.getBytes)
            }
          } yield ()

        override def get(ctx: Ref[RocksDB], id: String): Task[CompetitionState] =
          for {
            rdb <- ctx.get
            bytes = rdb.get(id.getBytes)
            state <- IO {
              objectMapper.createParser(bytes).readValueAs(classOf[CompetitionStateImpl])
            }
          } yield state

        override def exists(ctx: Ref[RocksDB], id: String): Task[Boolean] =
          for {
            rdb <- ctx.get
            bytes = rdb.get(id.getBytes)
          } yield bytes != null

      }

  }

  object CommandEventOperations {
    def apply[F[+_], A, T](implicit
        F: CommandEventOperations[F, A, T]
    ): CommandEventOperations[F, A, T] = F
  }
  object IdOperations {
    def apply[F[_]](implicit F: IdOperations[F]): IdOperations[F] = F

    val live: IdOperations[Task] =
      (_: CompetitorDTO) =>
        Task {
          util.UUID.randomUUID().toString
        }

  }
  object CrudOperations {
    def apply[F[+_], A, Context[_], DB](implicit
        F: CrudOperations[F, A, Context, DB]
    ): CrudOperations[F, A, Context, DB] = F
  }

  def mapRecord[F[
      +_
  ]: Monad: CommandMapping: EventMapping: StateOperations.Service: IdOperations: EventOperations, Context[
      _
  ], DB](appConfig: AppConfig, ctx: Context[DB], record: CommittableRecord[Long, CommandDTO])(
      implicit crudOps: CompetitionStateCrudOperations[F, Context, DB]
  ): F[Either[Errors.Error, Seq[ProducerRecord[String, EventDTO]]]] = {
    def toProducerRecord(events: EventDTO): ProducerRecord[String, EventDTO] = {
      new ProducerRecord(appConfig.producer.topic, events.getCompetitionId, events)
    }

    val either: EitherT[F, Errors.Error, Seq[ProducerRecord[String, EventDTO]]] =
      for {
        mapped      <- EitherT.right[Errors.Error](Mapping.mapCommandDto(record.value))
        queryConfig <- EitherT.right[Errors.Error](StateOperations.createConfig(mapped))
        latestState <- EitherT[F, Errors.Error, CompetitionState](
          StateOperations.getLatestState(queryConfig)
        )
        eventsToApply <- EitherT[F, Errors.Error, Seq[EventDTO]](
          CommandProcessors.process(mapped, latestState)
        )
        eventsAndNewState <- EitherT[F, Errors.Error, (Seq[EventDTO], CompetitionState)](
          eventsToApply
            .map(event =>
              Monad[F]
                .flatMap(Mapping.mapEventDto(event))(EventProcessors.applyEvent(_, latestState))
            )
            .reduce((a, b) => Monad[F].flatMap(a)(_ => b))
        )
        _ <- EitherT.liftF(crudOps.add(ctx, eventsAndNewState._2))
        records = eventsAndNewState._1.map(toProducerRecord)
      } yield records
    either.value
  }

}
