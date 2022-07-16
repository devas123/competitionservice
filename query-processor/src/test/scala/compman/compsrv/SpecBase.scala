package compman.compsrv

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.{BeforeAndAfter, CancelAfterFailure, Suite}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

abstract class SpecBase extends
    AnyFunSuiteLike
    with Matchers
    with ScalaFutures
    with Eventually
    with BeforeAndAfter
    with CancelAfterFailure {
  this: Suite =>
  protected val actorTestKit: ActorTestKit = ActorTestKit()
}
