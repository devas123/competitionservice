package compman.compsrv.query.service

import akka.actor.typed.ActorRef
import cats.effect.{std, IO}
import compman.compsrv.query.actors.behavior.WebsocketConnectionSupervisor
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO
import compservice.model.protobuf.event.Event
import fs2.concurrent.SignallingRef
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Close
import scodec.bits.ByteVector

import java.util.UUID
import scala.concurrent.duration.DurationInt

object WebsocketService {

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._

  def wsRoutes(websocketConnectionHandler: ActorRef[WebsocketConnectionSupervisor.ApiCommand]): HttpRoutes[ServiceIO] =
    HttpRoutes.of[ServiceIO] { case GET -> Root / "events" / competitionId =>
      for {
        queue <- std.Queue.dropping[IO, Event](100)
        clientId = UUID.randomUUID().toString
        completed <- SignallingRef.of[ServiceIO, Boolean](false)
        fs2S = fs2.Stream.fromQueueUnterminated(queue).interruptWhen(completed)
        ws <- WebSocketBuilder[ServiceIO].build(
          fs2S.map(event => WebSocketFrame.Binary(ByteVector(event.toByteArray))),
          s =>
            s.evalMap({
              case Close(_) => IO {
                  websocketConnectionHandler !
                    WebsocketConnectionSupervisor
                      .WebsocketConnectionClosed(clientId = clientId, competitionId = competitionId)
                }
              case WebSocketFrame.Text(_, _) => IO.unit
              case _                         => IO.unit
            }).onFinalize(IO {
              websocketConnectionHandler !
                WebsocketConnectionSupervisor
                  .WebsocketConnectionClosed(clientId = clientId, competitionId = competitionId)

            }).timeout(5.minutes)
        )
        _ <- IO {
          websocketConnectionHandler ! WebsocketConnectionSupervisor.WebsocketConnectionRequest(
            clientId = clientId,
            competitionId = competitionId,
            queue = queue,
            terminatedSignal = completed
          )
        }
      } yield ws
    }
}
