package compman.compsrv.account

import akka.actor.typed.ActorSystem
import compman.compsrv.account.actors.AccountServiceMainActor

object AccountServiceMain extends App {
  ActorSystem(AccountServiceMainActor.behavior(), "AccountServiceMain")
}
