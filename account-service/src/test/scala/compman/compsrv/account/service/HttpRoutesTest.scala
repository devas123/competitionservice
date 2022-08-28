package compman.compsrv.account.service

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import compman.compsrv.account.actors.AccountRepositorySupervisorActor.{AccountServiceQueryRequest, GetAccountRequest}
import org.http4s.{Method, Request}
import org.http4s.implicits.http4sLiteralsSyntax

class HttpRoutesTest extends SpecBase {

  val version                                    = "v1"
  implicit val actorSystem: ActorSystem[Nothing] = actorTestKit.system
  implicit val runtime: IORuntime                = cats.effect.unsafe.IORuntime.global

  test("Send GetAccountRequest") {
    val repositoryActor: TestProbe[AccountServiceQueryRequest] = actorTestKit
      .createTestProbe[AccountServiceQueryRequest]()
    // tests:
    val response = HttpServer.routes(repositoryActor.ref).orNotFound.run(Request[IO](Method.GET, uri"/account/test"))
      .unsafeRunSync()
    repositoryActor.expectMessageType[GetAccountRequest]
  }

}
