package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.behavior.WebsocketConnection
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.events.EventDTO
import ActorSystem.ActorConfig
import compman.compsrv.query.service.repository.TestEntities
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.util.UUID

object WebsocketConnectionTest extends DefaultRunnableSpec with TestEntities {
  override def spec: ZSpec[Any, Throwable] =
    (suite("Websocket connection actor suite")(
      testM("should handle connect and receive messages and stop") {
        for {
          actorSystem <- ActorSystem("test")
          wsActor <- actorSystem
            .make("WsActor", ActorConfig(), WebsocketConnection.initialState, WebsocketConnection.behavior)
          queue    <- Queue.unbounded[EventDTO]
          clientId <- ZIO.effect(UUID.randomUUID().toString)
          _        <- wsActor ! WebsocketConnection.AddWebSocketConnection(clientId, queue)
          test <- (for {
            msg <- queue.takeN(1)
            _   <- Logging.info(msg.mkString("\n"))
          } yield ()).fork
          _ <- wsActor ! WebsocketConnection.ReceivedEvent(new EventDTO())
          _        <- wsActor ? WebsocketConnection.Stop
          _ <- test.join
          shutdown <- queue.isShutdown
        } yield assert(shutdown)(isTrue)
      }
    ) @@ sequential)
      .provideLayer(Clock.live ++ CompetitionLogging.Live.loggingLayer ++ Blocking.live ++ zio.console.Console.live)
}
