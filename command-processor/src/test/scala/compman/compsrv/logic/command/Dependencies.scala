package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Errors
import compman.compsrv.model.Mapping.EventMapping
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.common.MessageInfo.Payload
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model._

import java.util.UUID

object Dependencies {
  implicit val idOps: IdOperations[Eval] = new IdOperations[Eval] {
    override def generateIdIfMissing(id: Option[String]): Eval[String] = id.map(Eval.now).getOrElse(uid)

    override def uid: Eval[String] = Eval.later(UUID.randomUUID().toString)

    override def fightId(stageId: String, groupId: String): Eval[String] = uid

    override def competitorId(competitor: Competitor): Eval[String] = uid

    override def categoryId(category: CategoryDescriptor): Eval[String] = uid

    override def registrationPeriodId(period: RegistrationPeriod): Eval[String] = uid

    override def registrationGroupId(group: RegistrationGroup): Eval[String] = uid
  }

  implicit val mapping: EventMapping[Eval] = (dto: Event) => Eval.later(EventMapping.mapEvent(dto))

  implicit val logging: CompetitionLogging.Service[Eval] = new CompetitionLogging.Service[Eval] {
    override def info(msg: => String): Eval[Unit] = Eval.now(println(msg))

    override def info(msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def info(error: Throwable, msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def error(msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def error(error: Throwable, msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def error(msg: => String): Eval[Unit] = Eval.now(println(msg))

    override def warn(msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def warn(error: Throwable, msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def warn(msg: => String): Eval[Unit] = Eval.now(println(msg))

    override def debug(msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def debug(error: Throwable, msg: => String, args: Any*): Eval[Unit] = Eval.now(println(msg))

    override def debug(msg: => String): Eval[Unit] = Eval.now(println(msg))
  }

  implicit val eventOps: EventOperations[Eval] = new EventOperations[Eval] {
    override def lift(obj: => Seq[Event]): Eval[Seq[Event]] = Eval.later(obj)

    override def create(
      `type`: EventType,
      competitionId: Option[String],
      competitorId: Option[String],
      fightId: Option[String],
      categoryId: Option[String],
      payload: Option[Payload]
    ): Eval[Event] = {
      val evt = Event().withMessageInfo(
        MessageInfo().update(_.payload.setIfDefined(payload), _.competitionId.setIfDefined(competitionId))
      ).withType(`type`)
      Eval.later(evt)
    }

    override def error(error: => Errors.Error): Eval[Either[Errors.Error, Event]] = Eval.now(Left(error))
  }

  implicit val selectInterpreter: Interpreter[Eval] = Interpreter.asTask[Eval]

}
