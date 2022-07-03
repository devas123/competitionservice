package compman.compsrv.logic.command

import cats.{Eval, Monad}
import cats.data.EitherT
import cats.implicits.toFoldableOps
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.Operations
import compman.compsrv.model.Errors
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.command.Commands.SetFightResultCommand
import compservice.model.protobuf.commandpayload.SetFightResultPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.common.MessageInfo.Payload
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model._

import java.util.UUID
import scala.util.Random

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
      val evt = Event().withMessageInfo(MessageInfo().update(
        _.payload.setIfDefined(payload),
        _.competitionId.setIfDefined(competitionId),
        _.categoryId.setIfDefined(categoryId)
      )).withType(`type`)
      Eval.later(evt)
    }

    override def error(error: => Errors.Error): Eval[Either[Errors.Error, Event]] = Eval.now(Left(error))
  }

  implicit val selectInterpreter: Interpreter[Eval] = Interpreter.asTask[Eval]

  def progressStage[F[+_]: Monad: IdOperations: EventMapping: EventOperations: CompetitionLogging.Service: Interpreter](
    state: CommandProcessorCompetitionState,
    stageId: String,
    eventsContainer: Seq[Event]
  ): EitherT[F, Errors.Error, (CommandProcessorCompetitionState, Seq[Event])] = {
    for {
      fight <- EitherT.pure[F, Errors.Error](state.fights.values.find(f =>
        f.stageId == stageId && f.status != FightStatus.UNCOMPLETABLE && f.status != FightStatus.FINISHED &&
          f.scores.size >= 2 && f.scores.forall(_.competitorId.isDefined)
      ))
      newState <- fight match {
        case Some(f) => for {
            payload <- EitherT.pure[F, Errors.Error](
              SetFightResultPayload().withFightId(f.id).withStatus(FightStatus.FINISHED)
                .withFightResult(FightResult().withReason("dota2").withResultTypeId("_default_win_points").withWinnerId(
                  Random.shuffle(f.scores).head.getCompetitorId
                )).withScores(f.scores)
            )
            cmd = SetFightResultCommand(
              payload = Some(payload),
              competitionId = Some(state.id),
              categoryId = Some(f.categoryId)
            )
            events <- EitherT[F, Errors.Error, Seq[Event]](SetFightResultProc[F](state).apply(cmd))
            newState <- EitherT.liftF[F, Errors.Error, CommandProcessorCompetitionState](
              events.toList.foldM(state)((s, e) => Operations.applyEvent[F](s, e))
            )
            res <- progressStage[F](newState, stageId, eventsContainer ++ events)
          } yield res
        case None => EitherT.pure[F, Errors.Error]((state, eventsContainer))
      }
    } yield newState
  }

}
