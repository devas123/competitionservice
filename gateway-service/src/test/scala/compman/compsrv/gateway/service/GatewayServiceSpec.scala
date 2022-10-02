package compman.compsrv.gateway.service

import compman.compsrv.gateway.SpecBase
import compman.compsrv.gateway.actors.CommandForwardingActor.GatewayApiCommand

class GatewayServiceSpec extends SpecBase {
  test("Should authenticate") {
    actorTestKit.createTestProbe[GatewayApiCommand]()
    // tests:
//    GatewayService.service(apiActor.ref, ).orNotFound.run(Request[IO](Method.GET, uri"/account/test")).unsafeRunSync()
//    apiActor.expectMessageType[GetAccountRequest]
  }

}
