package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CompetitorDTO, RegistrationGroupDTO, RegistrationPeriodDTO}
import compman.compsrv.model.events.{EventDTO, EventType}

import java.util.UUID

object Dependencies {
  implicit val idOps: IdOperations[Eval] = new IdOperations[Eval] {
    override def generateIdIfMissing(id: Option[String]): Eval[String] = id.map(Eval.now).getOrElse(uid)

    override def uid: Eval[String] = Eval.later(UUID.randomUUID().toString)

    override def fightId(stageId: String, groupId: String): Eval[String] = uid

    override def competitorId(competitor: CompetitorDTO): Eval[String] = uid

    override def categoryId(category: CategoryDescriptorDTO): Eval[String] = uid

    override def registrationPeriodId(period: RegistrationPeriodDTO): Eval[String] = uid

    override def registrationGroupId(group: RegistrationGroupDTO): Eval[String] = uid
  }

  implicit val eventOps: EventOperations[Eval] = new EventOperations[Eval] {
    override def lift(obj: => Seq[EventDTO]): Eval[Seq[EventDTO]] = Eval.later(obj)

    override def create[P <: Payload](
                                       `type`: EventType,
                                       competitionId: Option[String],
                                       competitorId: Option[String],
                                       fightId: Option[String],
                                       categoryId: Option[String],
                                       payload: Option[P]
                                     ): Eval[EventDTO] = {
      val evt = new EventDTO()
      evt.setPayload(payload.orNull)
      evt.setType(`type`)
      evt.setCompetitionId(competitionId.orNull)
      Eval.later(evt)
    }

    override def error(error: => Errors.Error): Eval[Either[Errors.Error, EventDTO]] = Eval.now(Left(error))
  }
}