package compman.compsrv.logic

import cats.data.EitherT
import cats.Monad
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.{info, CompetitionLogging}
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.{Errors, Mapping}
import compman.compsrv.model.Mapping.{CommandMapping, EventMapping}
import compservice.model.protobuf.command.Command
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.common.MessageInfo.Payload
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model.{CategoryDescriptor, Competitor, RegistrationGroup, RegistrationPeriod}
import zio.Task

import java.util
import java.util.UUID

object Operations {

  trait IdOperations[F[_]] {
    def generateIdIfMissing(id: Option[String] = None): F[String]
    def uid: F[String]
    def fightId(stageId: String, groupId: String): F[String]
    def competitorId(competitor: Competitor): F[String]
    def categoryId(category: CategoryDescriptor): F[String]
    def registrationPeriodId(period: RegistrationPeriod): F[String]
    def registrationGroupId(group: RegistrationGroup): F[String]
  }

  trait CommandEventOperations[F[+_], A] {
    def lift(obj: => Seq[A]): F[Seq[A]]
    def create(
      `type`: EventType,
      competitionId: Option[String] = None,
      competitorId: Option[String] = None,
      fightId: Option[String] = None,
      categoryId: Option[String] = None,
      payload: Option[Payload]
    ): F[A]
    def error(error: => Errors.Error): F[Either[Errors.Error, A]]
  }
  trait EventOperations[F[+_]] extends CommandEventOperations[F, Event]

  object EventOperations {
    val live: EventOperations[LIO] = new EventOperations[LIO] {
      override def lift(obj: => Seq[Event]): LIO[Seq[Event]] = Task(obj)

      override def error(error: => Errors.Error): LIO[Either[Errors.Error, Event]] = Task { Left(error) }

      override def create(
        `type`: EventType,
        competitionId: Option[String],
        competitorId: Option[String],
        fightId: Option[String],
        categoryId: Option[String],
        payload: Option[Payload]
      ): LIO[Event] = Task {
        Event().withType(`type`).withMessageInfo(MessageInfo().withId(UUID.randomUUID().toString).update(
          _.competitionId.setIfDefined(competitionId),
          _.categoryId.setIfDefined(categoryId),
          _.competitorId.setIfDefined(competitorId),
          _.payload.setIfDefined(payload)
        ))
      }
    }

  }

  object CommandEventOperations {
    def apply[F[+_], A](implicit F: CommandEventOperations[F, A]): CommandEventOperations[F, A] = F
  }
  object IdOperations {
    def uid: String = UUID.randomUUID().toString

    def apply[F[_]](implicit F: IdOperations[F]): IdOperations[F] = F

    val live: IdOperations[LIO] = new IdOperations[LIO] {
      override def competitorId(competitor: Competitor): LIO[String]       = Task(util.UUID.randomUUID().toString)
      override def categoryId(competitor: CategoryDescriptor): LIO[String] = Task(util.UUID.randomUUID().toString)
      override def registrationPeriodId(competitor: RegistrationPeriod): LIO[String] =
        Task(util.UUID.randomUUID().toString)

      override def registrationGroupId(group: RegistrationGroup): LIO[String] = Task(util.UUID.randomUUID().toString)

      override def fightId(stageId: String, groupId: String): LIO[String] = Task(UUID.randomUUID().toString)

      override def uid: LIO[String] = Task(UUID.randomUUID().toString)

      override def generateIdIfMissing(id: Option[String]): LIO[String] =
        Task(id.filter(_.isEmpty).getOrElse(UUID.randomUUID().toString))
    }

  }

  def processStatelessCommand[F[
    +_
  ]: Monad: CommandMapping: IdOperations: CompetitionLogging.Service: EventOperations: Interpreter](
    command: Command
  ): F[Either[Errors.Error, Seq[Event]]] = {
    import cats.implicits._
    val either: EitherT[F, Errors.Error, Seq[Event]] = for {
      _             <- EitherT.liftF(info(s"Received command: $command"))
      mapped        <- EitherT.liftF(Mapping.mapCommandDto(command))
      _             <- EitherT.liftF(info(s"Mapped command: $mapped"))
      eventsToApply <- EitherT(StatelessCommandProcessors.process(mapped))
      _             <- EitherT.liftF(info(s"Received events: $eventsToApply"))
      numberOfEvents = eventsToApply.size
      enrichedEvents = eventsToApply.toList.mapWithIndex((ev, ind) =>
        enrichEvent(command, numberOfEvents, ev, ind)
      )
      _ <- EitherT.liftF(info(s"Returning events: $enrichedEvents"))
    } yield enrichedEvents
    either.value
  }

  private def enrichEvent(command: Command, numberOfEvents: Int, ev: Event, ind: Int) = {
    ev.withLocalEventNumber(ind).withNumberOfEventsInBatch(numberOfEvents)
      .withTimestamp(Timestamp.fromJavaProto(Timestamps.fromNanos(System.currentTimeMillis())))
      .withMessageInfo(ev.getMessageInfo.update(_.correlationId.setIfDefined(command.messageInfo.flatMap(_.id))))
  }

  def processStatefulCommand[F[
    +_
  ]: Monad: CommandMapping: IdOperations: CompetitionLogging.Service: EventOperations: Interpreter](
    latestState: CompetitionState,
    command: Command
  ): F[Either[Errors.Error, Seq[Event]]] = {
    import cats.implicits._
    val either: EitherT[F, Errors.Error, Seq[Event]] = for {
      _             <- EitherT.liftF(info(s"Received command: $command"))
      mapped        <- EitherT.liftF(Mapping.mapCommandDto(command))
      _             <- EitherT.liftF(info(s"Mapped command: $mapped"))
      eventsToApply <- EitherT(CompetitionCommandProcessors.process(mapped, latestState))
      _             <- EitherT.liftF(info(s"Received events: $eventsToApply"))
      n              = latestState.revision
      numberOfEvents = eventsToApply.size
      enrichedEvents = eventsToApply.toList.mapWithIndex((ev, ind) =>
        enrichEvent(command, numberOfEvents, ev, ind).withVersion(n + ind)
      )
      _ <- EitherT.liftF(info(s"Returning events: $enrichedEvents"))
    } yield enrichedEvents
    either.value
  }

  def applyEvent[F[+_]: CompetitionLogging.Service: Monad: EventMapping: IdOperations: EventOperations](
    latestState: CompetitionState,
    event: Event
  ): F[CompetitionState] = {
    import cats.implicits._
    for {
      mapped <- EventMapping.mapEventDto(event)
      result <- EventProcessors.applyEvent[F](mapped, latestState)
    } yield result
  }

}
