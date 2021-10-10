package compman.compsrv.service

import compman.compsrv.logic.fights.BracketsUtils
import zio.test._
import zio.{Task, ZIO}
import zio.test.Assertion._
import zio.interop.catz._

object BracketsUtilsTest extends DefaultRunnableSpec with TestEntities {
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("Brackets utils") {
    testM("Should generate brackets") {
      for {
        _ <- ZIO.effect("")
        fights <- BracketsUtils
          .generateEmptyWinnerRoundsForCategory[Task](competitionId, categoryId, stageId, 8, BigDecimal(10).bigDecimal)
        res = fights.fold(_ => List.empty, identity)
      } yield assert(res.size)(equalTo(15))
    }
  }
}
