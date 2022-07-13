package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.WebsocketCompetitionConnectionSupervisor
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.service.repository.TestEntities
import compservice.model.protobuf.event.Event
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.{durationInt, Duration}
import zio.logging.Logging
import zio.test._
import zio.test.TestAspect._

import java.util.UUID

object WebsocketConnectionTest extends DefaultRunnableSpec with TestEntities {
  import compman.compsrv.logic.actors.patterns.Patterns._
  implicit val timeout: Duration = 10.seconds

  override def spec: ZSpec[Any, Throwable] =
    (suite("Websocket connection actor suite")(
      testM("should handle connect and receive messages and stop") {
        ActorSystem("Test").use { actorSystem =>
          for {
            wsActor <- actorSystem
              .make("WsActor", ActorConfig(), WebsocketCompetitionConnectionSupervisor.initialState, WebsocketCompetitionConnectionSupervisor.behavior)
            queue <- Queue.unbounded[Event]
            clientId <- ZIO.effect(UUID.randomUUID().toString)
            _ <- wsActor ! WebsocketCompetitionConnectionSupervisor.AddWebSocketConnection(clientId, queue)
            test <- (for {
              msg <- queue.takeN(1)
              _ <- Logging.info(msg.mkString("\n"))
            } yield ()).fork
            _ <- wsActor ! WebsocketCompetitionConnectionSupervisor.ReceivedEvent(new Event())
            _ <- wsActor ? ((actor: ActorRef[Boolean]) => WebsocketCompetitionConnectionSupervisor.Stop(Some(actor)))
            _ <- test.join
            shutdown <- queue.isShutdown
          } yield assertTrue(shutdown)
        }
      }
    ) @@ sequential)
      .provideLayer(Clock.live ++ Compman.compsrv.interop.loggingLayer ++ Blocking.live ++ zio.console.Console.live)
}
