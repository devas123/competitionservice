package compman.compsrv.gateway.service

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import cats.data.{Kleisli, NonEmptyList}
import cats.effect.IO
import cats.implicits._
import cats.MonadThrow
import compman.compsrv.gateway.actors.CommandForwardingActor
import compman.compsrv.gateway.actors.CommandForwardingActor.GatewayApiCommand
import compman.compsrv.gateway.auth.jwt.{AuthUser, JwtAuthMiddleware}
import compman.compsrv.gateway.auth.jwt.JwtAuthTypes.{JwtNoValidation, JwtToken}
import compman.compsrv.gateway.config.{ProxyConfig, ProxyLocation}
import compman.compsrv.http4s
import compservice.model.protobuf.callback.CommandCallback
import compservice.model.protobuf.command.Command
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`X-Forwarded-For`, Host}
import org.slf4j.LoggerFactory
import pdi.jwt.JwtClaim

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

  def service(apiActor: ActorRef[GatewayApiCommand], client: Client[ServiceIO], proxyConfig: ProxyConfig)(implicit
    system: ActorSystem[_]
  ): HttpRoutes[ServiceIO] = {
    http4s.loggerMiddleware(internalService(apiActor))(log) <+> proxyService(client, proxyConfig)
  }

  def proxyService(client: Client[ServiceIO], proxyConfig: ProxyConfig): HttpRoutes[ServiceIO] = {
    val routes: HttpRoutes[ServiceIO] = HttpRoutes.of { case req: Request[ServiceIO] =>
      val pathRendered = req.uri.path.renderString
      val proxy        = proxyConfig.locations.find(e => pathRendered.startsWith(e._1)).map(_._2)
      proxy.fold(Response[ServiceIO](Status.NotFound).withEntity("No Route Found").pure[ServiceIO])(
        proxyThrough[ServiceIO](_).flatMap(uri =>
          client.toHttpApp(req.removeHeader[Host].withUri(uri.addPath(pathRendered)))
        )
      )
    }
    xForwardedMiddleware(routes)
  }

  private def proxyThrough[F[_]: MonadThrow](proxyLocation: ProxyLocation): F[Uri] = Uri
    .fromString(proxyLocation.toProxyPass).liftTo[F]

  def xForwardedMiddleware[G[_], F[_]](http: Http[G, F]): Http[G, F] = Kleisli { (req: Request[F]) =>
    req.remote.fold(http.run(req)) { remote =>
      val forwardedFor = req.headers.get[`X-Forwarded-For`]
        .fold(`X-Forwarded-For`(NonEmptyList.of(Some(remote.host))))(init =>
          `X-Forwarded-For`(init.values :+ remote.host.some)
        )
      val forwardedProtocol = req.uri.scheme.map(headers.`X-Forwarded-Proto`(_))

      val forwardedHost = req.headers.get[Host].map(host => "X-Forwarded-Host" -> Host.headerInstance.value(host))

      val init = req.putHeaders(forwardedFor)

      val second = forwardedProtocol.fold(init)(proto => init.putHeaders(proto))
      val third  = forwardedHost.fold(second)(host => second.putHeaders(host))
      http.run(third)
    }
  }

  private def internalService(
    apiActor: ActorRef[GatewayApiCommand]
  )(implicit system: ActorSystem[_]): HttpRoutes[ServiceIO] = {
    val unauthedRoutes = HttpRoutes.of[ServiceIO] { case req @ POST -> Root / "account" / "create" =>
      for {
        body    <- req.body.covary[ServiceIO].chunkAll.compile.toList
        command <- IO(body.flatMap(_.toList).toArray)
        _       <- IO(log.info(s"Forwarding create account request"))
        resp    <- Ok()
      } yield resp
    }
    val authedRoutes = AuthedRoutes.of[AuthUser, ServiceIO] {
      case req @ POST -> Root / "competition" / "command" :? CompetitionIdParamMatcher(competitionId) as _ /* user */ =>
        for {
          _ <- IO.raiseError(new RuntimeException("competition id missing"))
            .whenA(competitionId.isEmpty && !competitionId.contains(null))
          body         <- req.req.body.covary[ServiceIO].chunkAll.compile.toList
          commandBytes <- IO(body.flatMap(_.toList).toArray)
          command = Command.parseFrom(commandBytes)
          _ <- IO(log.info(s"Sending command for $competitionId, $command"))
          _ <- sendApiCommandAndReturnResponse(
            apiActor,
            _ => CommandForwardingActor.ForwardCommand(competitionId.get, commandBytes)
          )
          resp <- Ok()
        } yield resp
      case req @ POST -> Root / "competition" / "scommand" :? CompetitionIdParamMatcher(competitionId) as
          _ /* user */ => for {
          body    <- req.req.body.covary[ServiceIO].chunkAll.compile.toList
          command <- IO(body.flatMap(_.toList).toArray)
          _ <-
            IO(log.info(s"Sending command and awaiting response ${competitionId.fold("")(s => s"for competition $s")}"))
          resp <- sendApiCommandAndReturnResponse(apiActor, createCommandForwarderCommand(competitionId, command))
        } yield resp
    }
    val authenticate: JwtToken => JwtClaim => IO[Option[AuthUser]] =
      _ /*_token_*/ => _ /*_claim_*/ => AuthUser("joe").some.pure[IO]

    val jwtAuth = JwtNoValidation
//    val jwtAuth    = JwtAuth.hmac("53cr3t", JwtAlgorithm.HS256)
    val middleware = JwtAuthMiddleware[IO, AuthUser](jwtAuth, authenticate)
    middleware(authedRoutes)
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
}
