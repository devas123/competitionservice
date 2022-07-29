package compman.compsrv.gateway.service

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import cats.effect.IO
import cats.implicits.catsSyntaxApplicative
import compman.compsrv.gateway.actors.CommandForwardingActor
import compman.compsrv.gateway.actors.CommandForwardingActor.GatewayApiCommand
import compservice.model.protobuf.callback.CommandCallback
import compservice.model.protobuf.command.Command
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt

object GatewayService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    object CompetitionIdParamMatcher extends OptionalQueryParamDecoderMatcher[String]("competitionId")
  }

  implicit val timeout: Timeout = 20.seconds
  type ServiceIO[A] = IO[A]

  private val dsl = Http4sDsl[ServiceIO]
  private val log = LoggerFactory.getLogger("GatewayService")

  import dsl._
  import QueryParameters._

  def service(apiActor: ActorRef[GatewayApiCommand])(implicit system: ActorSystem[_]): HttpRoutes[ServiceIO] =
    HttpRoutes.of[ServiceIO] {
      case req @ POST -> Root / "competition" / "command" :? CompetitionIdParamMatcher(competitionId) => for {
          _ <- IO.raiseError(new RuntimeException("competition id missing"))
            .whenA(competitionId.isEmpty && !competitionId.contains(null))
          body         <- req.body.covary[ServiceIO].chunkAll.compile.toList
          commandBytes <- IO(body.flatMap(_.toList).toArray)
          command = Command.parseFrom(commandBytes)
          _ <- IO(log.info(s"Sending command for $competitionId, $command"))
          _ <- sendApiCommandAndReturnResponse(
            apiActor,
            _ => CommandForwardingActor.ForwardCommand(competitionId.get, commandBytes)
          )
          resp <- Ok()
        } yield resp
      case req @ POST -> Root / "competition" / "scommand" :? CompetitionIdParamMatcher(competitionId) => for {
          body    <- req.body.covary[ServiceIO].chunkAll.compile.toList
          command <- IO(body.flatMap(_.toList).toArray)
          _ <-
            IO(log.info(s"Sending command and awaiting response ${competitionId.fold("")(s => s"for competition $s")}"))
          resp <- sendApiCommandAndReturnResponse(apiActor, createCommandForwarderCommand(competitionId, command))
        } yield resp

    }
  private def createCommandForwarderCommand(competitionId: Option[String], body: Array[Byte])(
    replyTo: ActorRef[CommandCallback]
  ) = competitionId match {
    case Some(value) => CommandForwardingActor.SendCompetitionCommandAndWaitForResult(value, body)(replyTo)
    case None        => CommandForwardingActor.SendAcademyCommandAndWaitForResult(body)(replyTo)
  }

  private def sendApiCommandAndReturnResponse[Command](
    apiActor: ActorRef[Command],
    apiCommandWithCallbackCreator: ActorRef[CommandCallback] => Command
  )(implicit scheduler: Scheduler): ServiceIO[Response[ServiceIO]] = {
    IO.fromFuture[CommandCallback](IO(apiActor.ask(apiCommandWithCallbackCreator))).attempt.flatMap {
      case Left(_)      => InternalServerError()
      case Right(value) => Ok(value.toByteArray)
    }
  }

//  private def sendApiCommandAndReturnResponse[Command](
//    apiActor: ActorRef[Command],
//    apiCommandWithCallbackCreator: ActorRef[CommandCallback] => Command
//  ): ServiceIO[Response[ServiceIO]] = {
//    import compman.compsrv.logic.actors.patterns.Patterns._
//    for {
//      response <- (apiActor ? apiCommandWithCallbackCreator)
//        .onError(err => Logging.error(s"Error while getting response: $err"))
//      _ <- Logging.debug(s"Sending response: $response")
//      bytes = response.map(_.toByteArray)
//      _ <- Logging.debug(s"Sending bytes: ${new String(bytes.getOrElse(Array.empty))}")
//      m <- bytes match {
//        case Some(value) => Ok(value)
//        case None        => RequestTimeout()
//      }
//    } yield m
//  }

}
