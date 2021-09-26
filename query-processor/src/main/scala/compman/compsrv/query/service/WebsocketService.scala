package compman.compsrv.query.service

import cats.data.Kleisli
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.events.EventDTO
import compman.compsrv.query.actors.ActorRef
import compman.compsrv.query.actors.behavior.WebsocketConnectionSupervisor
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.CompetitionHttpApiService.ServiceIO
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import scodec.bits.ByteVector
import zio.{Queue, ZIO}
import zio.logging.Logging

import java.util.UUID

object WebsocketService {

  val decoder: ObjectMapper = ObjectMapperFactory.createObjectMapper

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._
  import zio.interop.catz._
  import zio.stream.interop.fs2z._

  def wsRoutes(
                websocketConnectionHandler: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
              ): Kleisli[ServiceIO, Request[ServiceIO], Response[ServiceIO]] = HttpRoutes
    .of[ServiceIO] { case GET -> Root / "events" / competitionId =>
      for {
        clientId <- ZIO.effect(UUID.randomUUID().toString)
        queue <- Queue.unbounded[EventDTO]
        stream = zio.stream.Stream.fromQueue(queue)
        fs2S = stream.toFs2Stream.asInstanceOf[fs2.Stream[ServiceIO, EventDTO]]
        ws <- WebSocketBuilder[ServiceIO].build(
          fs2S.map(event => WebSocketFrame.Binary(ByteVector(decoder.writeValueAsBytes(event)))),
          s =>
            s.evalMap({ case WebSocketFrame.Text(text, _) => Logging.info(s"Received a message $text") }).onFinalize(
              websocketConnectionHandler !
                WebsocketConnectionSupervisor
                  .WebsocketConnectionClosed(clientId = clientId, competitionId = competitionId)
            )
        )
        _ <- websocketConnectionHandler !
          WebsocketConnectionSupervisor
            .WebsocketConnectionRequest(clientId = clientId, competitionId = competitionId, queue = queue)
      } yield ws
    }.orNotFound
}