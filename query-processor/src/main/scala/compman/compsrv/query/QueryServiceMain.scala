package compman.compsrv.query

import akka.actor.typed.ActorSystem
import compman.compsrv.query.actors.behavior.QueryServiceMainActor

object QueryServiceMain extends scala.App {
  ActorSystem(QueryServiceMainActor.behavior(), "QueryServiceMain")
}
