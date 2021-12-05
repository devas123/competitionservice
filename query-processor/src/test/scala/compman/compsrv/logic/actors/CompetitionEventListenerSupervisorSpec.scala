package compman.compsrv.logic.actors

import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaTopicConfig}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.{CompetitionEventListener, CompetitionEventListenerSupervisor, WebsocketConnectionSupervisor}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.CompetitionProcessingStarted
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.model._
import compman.compsrv.query.serde.{ObjectMapperFactory, SerdeApi}
import compman.compsrv.query.service.EmbeddedKafkaBroker
import compman.compsrv.query.service.EmbeddedKafkaBroker.embeddedKafkaServer
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment._

import java.time.Instant

object CompetitionEventListenerSupervisorSpec extends DefaultRunnableSpec {
  private val notificationTopic = "notifications"
  private val loggingLayer = CompetitionLogging.Live.loggingLayer
  private val mapper            = ObjectMapperFactory.createObjectMapper

  override def spec: ZSpec[TestEnvironment, Any] = suite("Competition event listener") {
    testM("Should subscribe to topics") {
      {
        for {
          brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
          producerSettings  = ProducerSettings(List(brokerUrl))
          producer          = Producer.make(producerSettings)
          actorSystem           <- ActorSystem("test")
          competitions          <- Ref.make(Map.empty[String, ManagedCompetition])
          competitionProperties <- Ref.make(Map.empty[String, CompetitionProperties])
          categories            <- Ref.make(Map.empty[String, Category])
          competitors           <- Ref.make(Map.empty[String, Competitor])
          fights                <- Ref.make(Map.empty[String, Fight])
          periods               <- Ref.make(Map.empty[String, Period])
          registrationPeriods   <- Ref.make(Map.empty[String, RegistrationPeriod])
          registrationGroups    <- Ref.make(Map.empty[String, RegistrationGroup])
          stages                <- Ref.make(Map.empty[String, StageDescriptor])
          websocketSupervisor   <- TestKit[WebsocketConnectionSupervisor.ApiCommand](actorSystem)
          kafkaSupervisor <- actorSystem
            .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
          _ <- kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
          supervisorContext = CompetitionEventListenerSupervisor.Test(competitions)
          eventListenerContext = CompetitionEventListener.Test(
            Some(competitionProperties),
            Some(categories),
            Some(competitors),
            Some(fights),
            Some(periods),
            Some(registrationPeriods),
            Some(registrationGroups),
            Some(stages)
          )
          _ <- actorSystem.make(
            "competitionSupervisor",
            ActorConfig(),
            (),
            CompetitionEventListenerSupervisor.behavior[Any](
              notificationTopic,
              supervisorContext,
              kafkaSupervisor,
              eventListenerContext,
              websocketSupervisor.ref
            )
          )
          competitionId = "competitionId"
          notification = CompetitionProcessingStarted(
            competitionId,
            competitionId + "name",
            competitionId + "events",
            "creator",
            Instant.now(),
            Instant.now(),
            Instant.now(),
            "UTC",
            CompetitionStatus.CREATED
          )
          _ <- ZIO.sleep(10.seconds)
          _ <- producer.use(p =>
            Producer.produce(
              notificationTopic,
              competitionId,
              mapper.writeValueAsBytes(notification),
              Serde.string,
              SerdeApi.byteSerializer
            ).provide(Has(p))
          )
          _ <- ZIO.sleep(10.seconds)
        } yield assert(true)(isTrue)
      }.provideLayer(Clock.live ++ loggingLayer ++ Blocking.live ++ zio.console.Console.live)
    }
  } @@ aroundAll(embeddedKafkaServer)(kafka => URIO(kafka.stop()))
}
