package compman.compsrv.logic

import cats.data.EitherT
import cats.Monad
import compman.compsrv.config.AppConfig
import compman.compsrv.logic.Mapping.{CommandMapping, EventMapping}
import compman.compsrv.model._
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.repository.CompetitionStateCrudRepository
import org.apache.kafka.clients.producer.ProducerRecord
import zio.Task
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

  def mapRecord[F[
      +_
  ]: Monad: CommandMapping: EventMapping: StateOperations.Service: IdOperations: EventOperations](appConfig: AppConfig, db: CompetitionStateCrudRepository[F], record: CommittableRecord[String, CommandDTO]): F[Either[Errors.Error, Seq[ProducerRecord[String, EventDTO]]]] = {
    def toProducerRecord(events: EventDTO): ProducerRecord[String, EventDTO] = {
      new ProducerRecord(appConfig.producer.topic, events.getCompetitionId, events)
    }

    val either: EitherT[F, Errors.Error, Seq[ProducerRecord[String, EventDTO]]] =
      for {
        mapped      <- EitherT.right(Mapping.mapCommandDto(record.value))
        queryConfig <- EitherT.right(StateOperations.createConfig(mapped))
        latestState <- EitherT(
          StateOperations.getLatestState(queryConfig)
        )
        eventsToApply <- EitherT(
          CommandProcessors.process(mapped, latestState)
        )
        eventsAndNewState <- EitherT(
          eventsToApply
            .map(event =>
              Monad[F]
                .flatMap(Mapping.mapEventDto(event))(EventProcessors.applyEvent(_, latestState))
            )
            .reduce((a, b) => Monad[F].flatMap(a)(_ => b))
        )
        _ <- EitherT.liftF(db.add(eventsAndNewState._2))
        records = eventsAndNewState._1.map(toProducerRecord)
      } yield records
    either.value
  }

}
