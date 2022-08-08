package compman.compsrv.query

import akka.actor.typed.ActorSystem
import compman.compsrv.logic.actors.behavior.QueryServiceMainActor

object QueryServiceMain extends scala.App {
  ActorSystem(QueryServiceMainActor.behavior(), "QueryServiceMain")
}
