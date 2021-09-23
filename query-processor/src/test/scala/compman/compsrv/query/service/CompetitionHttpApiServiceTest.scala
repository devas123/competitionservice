package compman.compsrv.query.service

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.actors.behavior.CompetitionApiActor
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.behavior.CompetitionApiActor.Test
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.service.CompetitionHttpApiService.ServiceIO
import compman.compsrv.query.service.repository.TestEntities
import org.http4s._
import org.http4s.implicits._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

object CompetitionHttpApiServiceTest extends DefaultRunnableSpec with TestEntities {
  override def spec: ZSpec[Any, Throwable] =
    (suite("routes suite")(
      testM("root request returns Ok") {
        for {
          actorSystem <- ActorSystem("test")
          managedCompetitions <- Ref.make(Map.empty[String, ManagedCompetition])
          _ <- managedCompetitions.update(m => m + (managedCompetition.competitionId -> managedCompetition))
          topic = "Test_topic"
          actor <- actorSystem.make(
            "test",
            ActorConfig(),
            CompetitionApiActor.initialState,
            CompetitionApiActor.behavior[Any](Test(managedCompetitions))
          )
          response <- CompetitionHttpApiService.service(actor).run(Request[ServiceIO](Method.GET, uri"/store/competition"))
        } yield assert(response.status)(equalTo(Status.Ok))
      }
//    testM("root request returns Ok, using assertM instead") {
//      assertM(CompetitionHttpApiService.service.run(Request[Task](Method.GET, uri"/")).map(_.status))(
//        equalTo(Status.Ok))
//    },
//    testM("Unknown url returns NotFound") {
//      assertM(CompetitionHttpApiService.service.run(Request[Task](Method.GET, uri"/a")).map(_.status))(
//        equalTo(Status.NotFound))
//    },
//    testM("root request body returns hello!") {
//      val io = for {
//        response <- CompetitionHttpApiService.service.run(Request[Task](Method.GET, uri"/"))
//        body <- response.body.compile.toVector.map(x => x.map(_.toChar).mkString(""))
//      } yield body
//      assertM(io)(equalTo("hello!"))
//    }
    ) @@ sequential).provideLayer(Clock.live ++ CompetitionLogging.Live.loggingLayer ++ Blocking.live)
}
