package compman.compsrv.logic.actors

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.util.Timeout
import cats.effect.{std, IO}
import compman.compsrv.query.service.repository.TestEntities
import compman.compsrv.SpecBase
import compman.compsrv.query.actors.behavior.{WebsocketCompetitionConnectionSupervisor, WithIORuntime}
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO
import compservice.model.protobuf.event.Event
import fs2.concurrent.SignallingRef

import java.util.UUID
import scala.concurrent.duration.DurationInt

class WebsocketConnectionTest extends SpecBase with TestEntities with WithIORuntime {
  implicit val timeout: Timeout = 10.seconds

  test("should handle connect and receive messages and stop") {
    val competitionId = "competitionId"
    val wsActor       = actorTestKit.spawn(WebsocketCompetitionConnectionSupervisor.behavior(competitionId), "WsActor")
    implicit val k: Scheduler = schedulerFromActorSystem(actorTestKit.system)

    val effect = for {
      queue     <- std.Queue.dropping[IO, Event](100)
      completed <- SignallingRef.of[ServiceIO, Boolean](false)
      clientId = UUID.randomUUID().toString
      _ <- IO {
        wsActor ! WebsocketCompetitionConnectionSupervisor.AddWebSocketConnection(clientId, queue, completed)
        wsActor ! WebsocketCompetitionConnectionSupervisor.ReceivedEvent(new Event())
        wsActor ? ((actor: ActorRef[Boolean]) => WebsocketCompetitionConnectionSupervisor.Stop(Some(actor)))
      }
      msg <- queue.take
    } yield assert(msg != null)
    effect.unsafeRunSync()
  }
}
