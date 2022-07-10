package compman.compsrv.query.service

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.api.CompetitionApiActor.Test
import compman.compsrv.logic.actors.{ActorSystem, TestKit}
import compman.compsrv.logic.actors.behavior.api.AcademyApiActor.AcademyApiCommand
import compman.compsrv.logic.actors.behavior.api.CompetitionApiActor
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO
import compman.compsrv.query.service.repository.TestEntities
import compservice.model.protobuf.query.QueryServiceResponse
import org.http4s._
import org.http4s.implicits._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.logging.Logging
import zio.test._
import zio.test.TestAspect._

object CompetitionHttpApiServiceTest extends DefaultRunnableSpec with TestEntities {
  override def spec: ZSpec[Any, Throwable] =
    (suite("routes suite")(
      testM("root request returns Ok") {
        ActorSystem("test").use { actorSystem =>
          for {
            managedCompetitions <- Ref.make(Map.empty[String, ManagedCompetition])
            _ <- managedCompetitions.update(m => m + (managedCompetition.id -> managedCompetition))
            actor <- actorSystem.make(
              "test",
              ActorConfig(),
              CompetitionApiActor.initialState,
              CompetitionApiActor.behavior[Any](Test(managedCompetitions))
            )
            academyApiActor <- TestKit[AcademyApiCommand](actorSystem)
            response <- QueryHttpApiService.service(actor, academyApiActor.ref).orNotFound
              .run(Request[ServiceIO](Method.GET, uri"/competition"))
            body <- response.body.compile.toVector
            _ <- Logging.info(new String(body.toArray))
            comp = QueryServiceResponse.parseFrom(body.toArray)
          } yield assertTrue(response.status == Status.Ok) && assertTrue(comp.getGetAllCompetitionsResponse.managedCompetitions.size == 1)
        }
      }
    ) @@ sequential)
      .provideLayer(Clock.live ++ Compman.compsrv.interop.loggingLayer ++ Blocking.live ++ zio.console.Console.live)
}
