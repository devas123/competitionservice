package compman.compsrv.logic.command

import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class FightEditorApplyChangesProcSpec  extends AnyFunSuite with BeforeAndAfter {

//  before {
//  }

  test("Mock test") {
    assert( "a" == "a")
  }


  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
