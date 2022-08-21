package compman.compsrv.query.service

import akka.actor.typed.ActorSystem
import compman.compsrv.query.actors.behavior.api.AcademyApiActor.AcademyApiCommand
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO
import compman.compsrv.query.service.repository.TestEntities
import compman.compsrv.SpecBase
import compman.compsrv.query.actors.behavior.api.CompetitionApiActor
import compman.compsrv.query.actors.behavior.WithIORuntime
import compservice.model.protobuf.query.QueryServiceResponse
import org.http4s._
import org.http4s.implicits._

import java.util.concurrent.atomic.AtomicReference

class CompetitionHttpApiServiceTest extends SpecBase with TestEntities with WithIORuntime {
  test("root request returns Ok") {
    val managedCompetitions = new AtomicReference(Map.empty[String, ManagedCompetition])
    managedCompetitions.updateAndGet(m => m + (managedCompetition.id -> managedCompetition))
    val actor = actorTestKit.spawn(CompetitionApiActor.behavior(CompetitionApiActor.Test(managedCompetitions)), "test")
    val academyApiActor                            = actorTestKit.createTestProbe[AcademyApiCommand]()
    implicit val actorSystem: ActorSystem[Nothing] = actorTestKit.system
    val response = QueryHttpApiService.service(actor, academyApiActor.ref).orNotFound
      .run(Request[ServiceIO](Method.GET, uri"/competition")).unsafeRunSync()
    val body = response.body.compile.toVector.unsafeRunSync()
    val comp = QueryServiceResponse.parseFrom(body.toArray)
    assert(response.status == Status.Ok)
    assert(comp.getGetAllCompetitionsResponse.managedCompetitions.size == 1)
  }
}
