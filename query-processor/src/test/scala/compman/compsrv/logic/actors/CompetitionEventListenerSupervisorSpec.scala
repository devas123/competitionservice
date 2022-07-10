package compman.compsrv.logic.actors

import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaTopicConfig}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.{CompetitionEventListener, CompetitionEventListenerSupervisor, WebsocketConnectionSupervisor}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.extensions.InstantOps
import compman.compsrv.query.model._
import compman.compsrv.query.service.EmbeddedKafkaBroker
import compman.compsrv.query.service.EmbeddedKafkaBroker.embeddedKafkaServer
import compservice.model.protobuf.model.{CompetitionProcessingStarted, CompetitionProcessorNotification, CompetitionStatus}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde
import zio.test._
import zio.test.TestAspect._
import zio.test.environment._

import java.time.Instant

object CompetitionEventListenerSupervisorSpec extends DefaultRunnableSpec {
  private val notificationTopic = "notifications"
  private val callbackTopic = "callback"
  private val loggingLayer      = compman.compsrv.interop.loggingLayer

  override def spec: ZSpec[TestEnvironment, Any] = suite("Competition event listener") {
    testM("Should subscribe to topics") {
      {
        ActorSystem("Test").use { actorSystem =>
          for {
            brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
            producerSettings = ProducerSettings(List(brokerUrl))
            producer         = Producer.make(producerSettings)
            competitions          <- Ref.make(Map.empty[String, ManagedCompetition])
            competitionProperties <- Ref.make(Map.empty[String, CompetitionProperties])
            categories            <- Ref.make(Map.empty[String, Category])
            competitors           <- Ref.make(Map.empty[String, Competitor])
            fights                <- Ref.make(Map.empty[String, Fight])
            periods               <- Ref.make(Map.empty[String, Period])
            registrationInfo   <- Ref.make(Map.empty[String, RegistrationInfo])
            stages                <- Ref.make(Map.empty[String, StageDescriptor])
            websocketSupervisor   <- TestKit[WebsocketConnectionSupervisor.ApiCommand](actorSystem)
            kafkaSupervisor <- actorSystem
              .make("kafkaSupervisor", ActorConfig(), KafkaSupervisor.initialState, KafkaSupervisor.behavior[Any](List(brokerUrl)))
            _ <- kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
            _ <- kafkaSupervisor ! CreateTopicIfMissing(callbackTopic, KafkaTopicConfig())
            supervisorContext = CompetitionEventListenerSupervisor.Test(competitions)
            eventListenerContext = CompetitionEventListener.Test(
              Some(competitionProperties),
              Some(categories),
              Some(competitors),
              Some(fights),
              Some(periods),
              Some(registrationInfo),
              Some(stages)
            )
            _ <- actorSystem.make(
              "competitionSupervisor",
              ActorConfig(),
              (),
              CompetitionEventListenerSupervisor.behavior[Any](
                notificationTopic,
                callbackTopic,
                supervisorContext,
                kafkaSupervisor,
                eventListenerContext,
                websocketSupervisor.ref
              )
            )
            competitionId = "competitionId"
            notification = CompetitionProcessorNotification().withStarted(CompetitionProcessingStarted(
              competitionId,
              competitionId + "name",
              competitionId + "events",
              "creator",
              Some(Instant.now().asTimestamp),
              Some(Instant.now().asTimestamp),
              Some(Instant.now().asTimestamp),
              "UTC",
              CompetitionStatus.CREATED
            ))
            _ <- ZIO.sleep(10.seconds)
            _ <- producer.use(p =>
              Producer.produce(
                notificationTopic,
                competitionId,
                notification.toByteArray,
                serde.Serde.string,
                serde.Serde.byteArray
              ).provide(Has(p))
            )
            _ <- ZIO.sleep(10.seconds)
          } yield assertTrue(true)
        }
      }.provideLayer(Clock.live ++ loggingLayer ++ Blocking.live ++ zio.console.Console.live)
    }
  } @@ aroundAll(embeddedKafkaServer)(kafka => URIO(kafka.stop()))
}
