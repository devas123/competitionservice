package compman.compsrv.query.service

import compman.compsrv.logic.actors.behavior.CompetitionApiActor
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.CompetitionApiActor.Test
import compman.compsrv.logic.actors.ActorSystem
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.serde.ObjectMapperFactory
import compman.compsrv.query.service.CompetitionHttpApiService.ServiceIO
import compman.compsrv.query.service.repository.TestEntities
import org.http4s._
import org.http4s.implicits._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object CompetitionHttpApiServiceTest extends DefaultRunnableSpec with TestEntities {
  private val mapper = ObjectMapperFactory.createObjectMapper
  override def spec: ZSpec[Any, Throwable] =
    (suite("routes suite")(
      testM("root request returns Ok") {
        for {
          actorSystem         <- ActorSystem("test")
          managedCompetitions <- Ref.make(Map.empty[String, ManagedCompetition])
          _ <- managedCompetitions.update(m => m + (managedCompetition.id -> managedCompetition))
          actor <- actorSystem.make(
            "test",
            ActorConfig(),
            CompetitionApiActor.initialState,
            CompetitionApiActor.behavior[Any](Test(managedCompetitions))
          )
          response <- CompetitionHttpApiService.service(actor).orNotFound
            .run(Request[ServiceIO](Method.GET, uri"/competition"))
          body    <- response.body.compile.toVector
          _ <- Logging.info(new String(body.toArray))
          comp = mapper.readValue(body.toArray, classOf[Array[ManagedCompetition]])
        } yield assert(response.status)(equalTo(Status.Ok)) && assert(comp.length)(equalTo(1))
      }
    ) @@ sequential)
      .provideLayer(Clock.live ++ CompetitionLogging.Live.loggingLayer ++ Blocking.live ++ zio.console.Console.live)
}
