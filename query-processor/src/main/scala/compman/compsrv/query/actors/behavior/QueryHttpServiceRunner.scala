package compman.compsrv.query.actors.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO

object QueryHttpServiceRunner extends WithIORuntime {
  sealed trait QueryHttpServiceRunnerApi
  def behavior(srv: ServiceIO[Unit]): Behavior[QueryHttpServiceRunnerApi] = Behaviors.setup { context =>
    srv.unsafeRunSync()
    Behaviors.stopped(() => context.log.info("Query HTTP Server stopped."))
  }
}
