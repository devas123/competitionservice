package compman.compsrv.query.actors

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.behavior.{CompetitionEventListener, CompetitionEventListenerSupervisor, WebsocketConnectionSupervisor}
import compman.compsrv.query.kafka.EmbeddedKafkaBroker
import compman.compsrv.query.model._
import compman.compsrv.query.service.kafka.EventStreamingService
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Ref, URIO, ZIO}


object CompetitionEventListenerSupervisorTest extends DefaultRunnableSpec {
  private val notificationTopic = "notifications"
  private val brokerUrl = s"localhost:${EmbeddedKafkaBroker.port}"
  private val loggingLayer = CompetitionLogging.Live.loggingLayer

  override def spec: ZSpec[TestEnvironment, Any] = suite("Competition event listener") {
    val kafkaBroker = EmbeddedKafkaBroker.embeddedKafkaServer.toManaged(kafka => URIO(kafka.stop(true))).toLayer
    import EmbeddedKafkaBroker._
    testM("Should subscribe to topics") {
      {
        for {
          _ <- ZIO.effect {
            EmbeddedKafkaBroker.createCustomTopic(notificationTopic, replicationFactor = 1, partitions = 1)
          }
          actorSystem <- ActorSystem("test")
          eventStreaming = EventStreamingService.live(List(brokerUrl))
          competitions <- Ref.make(Map.empty[String, ManagedCompetition])
          competitionProperties <- Ref.make(Map.empty[String, CompetitionProperties])
          categories <- Ref.make(Map.empty[String, Category])
          competitors <- Ref.make(Map.empty[String, Competitor])
          fights <- Ref.make(Map.empty[String, Fight])
          periods <- Ref.make(Map.empty[String, Period])
          registrationPeriods <- Ref.make(Map.empty[String, RegistrationPeriod])
          registrationGroups <- Ref.make(Map.empty[String, RegistrationGroup])
          stages <- Ref.make(Map.empty[String, StageDescriptor])
          websocketSupervisor <- TestKit[WebsocketConnectionSupervisor.ApiCommand](actorSystem)
          supervisorContext = CompetitionEventListenerSupervisor.Test(competitions)
          eventListenerContext = CompetitionEventListener.Test(Some(competitionProperties), Some(categories), Some(competitors), Some(fights), Some(periods), Some(registrationPeriods), Some(registrationGroups), Some(stages))
          competitionListenerActor <- actorSystem.make("competitionSupervisor", ActorConfig(), (), CompetitionEventListenerSupervisor.behavior(eventStreaming, notificationTopic, supervisorContext, eventListenerContext, websocketSupervisor.ref))
        } yield assert(true)(isTrue)
      }.provideLayer(Clock.live ++ loggingLayer ++ Blocking.live ++ zio.console.Console.live ++ kafkaBroker)
    }

  }
}
