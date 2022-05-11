package compman.compsrv.logic

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.{info, CompetitionLogging}
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model._
import compman.compsrv.model.Mapping.{CommandMapping, EventMapping}
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.{
  CategoryDescriptorDTO,
  CompetitorDTO,
  RegistrationGroupDTO,
  RegistrationPeriodDTO
}
import compman.compsrv.model.events.{EventDTO, EventType}
import zio.Task

import java.util
import java.util.UUID

object Operations {

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
    val live: EventOperations[LIO] = new EventOperations[LIO] {
      override def lift(obj: => Seq[EventDTO]): LIO[Seq[EventDTO]] = Task(obj)

      override def error(error: => Errors.Error): LIO[Either[Errors.Error, EventDTO]] = Task { Left(error) }

      override def create[P <: Payload](
        `type`: EventType,
        competitionId: Option[String],
        competitorId: Option[String],
        fightId: Option[String],
        categoryId: Option[String],
        payload: Option[P]
      ): LIO[EventDTO] = Task {
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
    def apply[F[+_], A, T](implicit F: CommandEventOperations[F, A, T]): CommandEventOperations[F, A, T] = F
  }
  object IdOperations {
    def apply[F[_]](implicit F: IdOperations[F]): IdOperations[F] = F

    val live: IdOperations[LIO] = new IdOperations[LIO] {
      override def competitorId(competitor: CompetitorDTO): LIO[String]       = Task(util.UUID.randomUUID().toString)
      override def categoryId(competitor: CategoryDescriptorDTO): LIO[String] = Task(util.UUID.randomUUID().toString)
      override def registrationPeriodId(competitor: RegistrationPeriodDTO): LIO[String] =
        Task(util.UUID.randomUUID().toString)

      override def registrationGroupId(group: RegistrationGroupDTO): LIO[String] = Task(util.UUID.randomUUID().toString)

      override def fightId(stageId: String, groupId: String): LIO[String] = Task(UUID.randomUUID().toString)

      override def uid: LIO[String] = Task(UUID.randomUUID().toString)

      override def generateIdIfMissing(id: Option[String]): LIO[String] = Task(id.getOrElse(UUID.randomUUID().toString))
    }

  }

  def processStatelessCommand[F[
    +_
  ]: Monad: CommandMapping: IdOperations: CompetitionLogging.Service: EventOperations: Interpreter](
    command: CommandDTO
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import cats.implicits._
    val either: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      _             <- EitherT.liftF(info(s"Received command: $command"))
      mapped        <- EitherT.liftF(Mapping.mapCommandDto(command))
      _             <- EitherT.liftF(info(s"Mapped command: $mapped"))
      eventsToApply <- EitherT(StatelessCommandProcessors.process(mapped))
      _             <- EitherT.liftF(info(s"Received events: $eventsToApply"))
      enrichedEvents = eventsToApply.toList.mapWithIndex((ev, ind) => {
        ev.setLocalEventNumber(ind).setCorrelationId(command.getId)
        ev
      })
      _ <- EitherT.liftF(info(s"Returning events: $enrichedEvents"))
    } yield enrichedEvents
    either.value
  }

  def processStatefulCommand[F[
    +_
  ]: Monad: CommandMapping: IdOperations: CompetitionLogging.Service: EventOperations: Interpreter](
    latestState: CompetitionState,
    command: CommandDTO
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import cats.implicits._
    val either: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      _             <- EitherT.liftF(info(s"Received command: $command"))
      mapped        <- EitherT.liftF(Mapping.mapCommandDto(command))
      _             <- EitherT.liftF(info(s"Mapped command: $mapped"))
      eventsToApply <- EitherT(CompetitionCommandProcessors.process(mapped, latestState))
      _             <- EitherT.liftF(info(s"Received events: $eventsToApply"))
      n = latestState.revision
      enrichedEvents = eventsToApply.toList.mapWithIndex((ev, ind) => {
        ev.setLocalEventNumber(n + ind).setCorrelationId(command.getId)
        ev
      })
      _ <- EitherT.liftF(info(s"Returning events: $enrichedEvents"))
    } yield enrichedEvents
    either.value
  }

  def applyEvent[F[+_]: CompetitionLogging.Service: Monad: EventMapping: IdOperations: EventOperations](
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
