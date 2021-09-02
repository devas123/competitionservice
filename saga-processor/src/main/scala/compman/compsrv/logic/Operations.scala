package compman.compsrv.logic

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Mapping.{CommandMapping, EventMapping}
import compman.compsrv.logic.service.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model._
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CompetitorDTO, RegistrationGroupDTO, RegistrationPeriodDTO}
import compman.compsrv.model.events.{EventDTO, EventType}
import zio.Task

import java.util
import java.util.UUID

object Operations {

  trait CrudOperations[F[+_], A, Context[_], DB] {
    def add(ctx: Context[DB], entity: A): F[Unit]
    def remove(ctx: Context[DB], id: String): F[Unit]
    def get(ctx: Context[DB], id: String): F[A]
    def exists(ctx: Context[DB], id: String): F[Boolean]
  }

  trait IdOperations[F[_]] {
    def generateIdIfMissing(id: Option[String] = None): F[String]
    def uid: F[String]
    def fightId(stageId: String, groupId: String): F[String]
    def competitorId(competitor: CompetitorDTO): F[String]
    def categoryId(category: CategoryDescriptorDTO): F[String]
    def registrationPeriodId(period: RegistrationPeriodDTO): F[String]
    def registrationGroupId(group: RegistrationGroupDTO): F[String]
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
      new IdOperations[Task] {
        override def competitorId(competitor: CompetitorDTO): Task[String] = Task(
          util.UUID.randomUUID().toString
        )
        override def categoryId(competitor: CategoryDescriptorDTO): Task[String] = Task(
          util.UUID.randomUUID().toString
        )
        override def registrationPeriodId(competitor: RegistrationPeriodDTO): Task[String] =
          Task(util.UUID.randomUUID().toString)

        override def registrationGroupId(group: RegistrationGroupDTO): Task[String] = Task(util.UUID.randomUUID().toString)

        override def fightId(stageId: String, groupId: String): Task[String] = Task(UUID.randomUUID().toString)

        override def uid: Task[String] = Task(UUID.randomUUID().toString)

        override def generateIdIfMissing(id: Option[String]): Task[String] = Task ( id.getOrElse(UUID.randomUUID().toString) )
      }

  }

  def processCommand[F[+_]: Monad: CommandMapping: IdOperations: EventOperations: Interpreter](
      latestState: CompetitionState,
      command: CommandDTO
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val either: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        mapped        <- EitherT.liftF(Mapping.mapCommandDto(command))
        eventsToApply <- EitherT(CommandProcessors.process(mapped, latestState))
      } yield eventsToApply
    either.value
  }

  def applyEvent[F[
      +_
  ]: Monad: EventMapping: StateOperations.Service: IdOperations: EventOperations](
      latestState: CompetitionState,
      event: EventDTO
  ): F[CompetitionState] = {
    import cats.implicits._
    for {
      mapped <- EventMapping.mapEventDto(event)
      result <- EventProcessors.applyEvent[F, Payload](mapped, latestState)
    } yield result
  }

}
