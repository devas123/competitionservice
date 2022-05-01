package compman.compsrv.query.service.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.{RegistrationGroupDTO, RegistrationPeriodDTO}
import compman.compsrv.model.event.Events.{CompetitionCreatedEvent, Event}
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

import scala.jdk.CollectionConverters._

object CompetitionCreatedProc {
  import cats.implicits._
  def apply[F[+_]: CompetitionLogging.Service: Monad: CompetitionUpdateOperations, P <: Payload]()
    : PartialFunction[Event[P], F[Unit]] = { case x: CompetitionCreatedEvent => apply[F](x) }

  private def apply[F[+_]: CompetitionLogging.Service: Monad: CompetitionUpdateOperations](
    event: CompetitionCreatedEvent
  ): F[Unit] = {
    for {
      payload          <- OptionT.fromOption[F](event.payload)
      competitionId    <- OptionT.fromOption[F](event.competitionId)
      registrationInfo <- OptionT.fromOption[F](Option(payload.getReginfo))
      _                <- OptionT.liftF(CompetitionLogging.Service[F].info(s"Payload is: $payload"))
      rawPeriods <- OptionT.fromOption[F](Option(registrationInfo.getRegistrationPeriods).orElse(Some(
        Map.empty[String, RegistrationPeriodDTO].asJava
      )))
      rawGroups <- OptionT.fromOption[F](Option(registrationInfo.getRegistrationGroups).orElse(Some(
        Map.empty[String, RegistrationGroupDTO].asJava
      )))
      regGroups  = rawGroups.asScala.values.toList.map(DtoMapping.mapRegistrationGroup(competitionId))
      regPeriods = rawPeriods.asScala.values.toList.map(DtoMapping.mapRegistrationPeriod(competitionId))

      compPropertiesDTO <- OptionT.fromOption[F](Option(payload.getProperties))
      competitionProperties <- OptionT
        .liftF(DtoMapping.mapCompetitionProperties[F](registrationOpen = false)(compPropertiesDTO))
      _ <- OptionT.liftF(CompetitionUpdateOperations[F].addCompetitionProperties(competitionProperties))
      _ <- OptionT.liftF(regGroups.traverse(CompetitionUpdateOperations[F].addRegistrationGroup))
      _ <- OptionT.liftF(regPeriods.traverse(CompetitionUpdateOperations[F].addRegistrationPeriod))
      _ <- OptionT.liftF(CompetitionLogging.Service[F].info(s"Done processing event!"))
    } yield ()
  }.value.map(_ => ())
}
