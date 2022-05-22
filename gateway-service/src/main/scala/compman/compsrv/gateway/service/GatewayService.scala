package compman.compsrv.gateway.service

import compman.compsrv.gateway.GatewayServiceMain.ServiceIO
import compman.compsrv.gateway.actors.CommandForwardingActor
import compman.compsrv.gateway.actors.CommandForwardingActor.GatewayApiCommand
import compman.compsrv.logic.actors.ActorRef
import compservice.model.protobuf.callback.CommandCallback
import compservice.model.protobuf.command.Command
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import zio.{Task, ZIO}
import zio.duration.{durationInt, Duration}
import zio.interop.catz._
import zio.logging.Logging

object GatewayService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    object CompetitionIdParamMatcher extends OptionalQueryParamDecoderMatcher[String]("competitionId")
  }

  implicit val timeout: Duration = 10.seconds

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._
  import QueryParameters._

  def service(apiActor: ActorRef[GatewayApiCommand]): HttpRoutes[ServiceIO] = HttpRoutes.of[ServiceIO] {
    case req @ POST -> Root / "competition" / "command" :? CompetitionIdParamMatcher(competitionId) => for {
      _ <- ZIO.fail(new RuntimeException("competition id missing"))
          .when(competitionId.isEmpty && !competitionId.contains(null))
      body    <- req.body.covary[ServiceIO].chunkAll.compile.toList
      commandBytes <- Task(body.flatMap(_.toList).toArray)
      command = Command.parseFrom(commandBytes)
      _       <- Logging.info(s"Sending command for $competitionId, $command")
      _ <- sendApiCommandAndReturnResponse(
          apiActor,
          _ => CommandForwardingActor.ForwardCommand(competitionId.get, commandBytes)
        )
      resp <- Ok()
      } yield resp
    case req @ POST -> Root / "competition" / "scommand" :? CompetitionIdParamMatcher(competitionId) =>
      for {
        _ <- ZIO.fail(new RuntimeException("competition id missing"))
          .when(competitionId.isEmpty && !competitionId.contains(null))
        body    <- req.body.covary[ServiceIO].chunkAll.compile.toList
        command <- Task(body.flatMap(_.toList).toArray)
        _ <- Logging
          .info(s"Sending command and awaiting response ${competitionId.fold("")(s => s"for competition $s")}")
        resp    <- sendApiCommandAndReturnResponse(apiActor, createCommandForwarderCommand(competitionId, command))
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
  ): ServiceIO[Response[ServiceIO]] = {
    import compman.compsrv.logic.actors.patterns.Patterns._
    for {
      response <- (apiActor ? apiCommandWithCallbackCreator)
        .onError(err => Logging.error(s"Error while getting response: $err"))
      _ <- Logging.debug(s"Sending response: $response")
      bytes = response.map(_.toByteArray)
      _ <- Logging.debug(s"Sending bytes: ${new String(bytes.getOrElse(Array.empty))}")
      m <- bytes match {
        case Some(value) => Ok(value)
        case None        => RequestTimeout()
      }
    } yield m
  }

}
