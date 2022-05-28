package compman.compsrv.query.service

import compman.compsrv.logic.actors.behavior.WebsocketConnectionSupervisor
import compman.compsrv.logic.actors.ActorRef
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO
import compservice.model.protobuf.event.Event
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Close
import scodec.bits.ByteVector
import zio.{Queue, ZIO}
import zio.logging.Logging

import java.util.UUID
import scala.concurrent.duration.DurationInt

object WebsocketService {

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._
  import zio.interop.catz._
  import zio.stream.interop.fs2z._

  def wsRoutes(websocketConnectionHandler: ActorRef[WebsocketConnectionSupervisor.ApiCommand]): HttpRoutes[ServiceIO] =
    HttpRoutes.of[ServiceIO] { case GET -> Root / "events" / competitionId =>
      for {
        clientId <- ZIO.effect(UUID.randomUUID().toString)
        queue    <- Queue.unbounded[Event]
        stream = zio.stream.Stream.fromQueue(queue)
        fs2S   = stream.toFs2Stream.asInstanceOf[fs2.Stream[ServiceIO, Event]]
        ws <- WebSocketBuilder[ServiceIO].build(
          fs2S.map(event => WebSocketFrame.Binary(ByteVector(event.toByteArray))),
          s =>
            s.evalMap({
              case Close(_) => Logging.info(s"Connection closed. Finishing") *>
                  (websocketConnectionHandler !
                    WebsocketConnectionSupervisor
                      .WebsocketConnectionClosed(clientId = clientId, competitionId = competitionId))
              case WebSocketFrame.Text(text, _) => Logging.info(s"Received a message $text")
              case x @ _                        => Logging.debug(s"Msg: $x")
            }).onFinalize(
              websocketConnectionHandler !
                WebsocketConnectionSupervisor
                  .WebsocketConnectionClosed(clientId = clientId, competitionId = competitionId)
            ).timeout(5.minutes)
        )
        _ <- websocketConnectionHandler !
          WebsocketConnectionSupervisor
            .WebsocketConnectionRequest(clientId = clientId, competitionId = competitionId, queue = queue)
      } yield ws
    }
}
