package compman.compsrv.logic.schedule

import compman.compsrv.logic.fight.CanFail
import compman.compsrv.model.Errors

import scala.collection.mutable

object GraphUtils {
  def getIndegree(graph: Array[List[Int]]): Array[Int] = {
    val inDegree = Array.fill(graph.length)(0)
    for {
      adj <- graph
      it  <- adj
    } { inDegree(it) += 1 }
    inDegree
  }

  object OrderingTypes extends Enumeration {
    type OrderingType = Value
    val Stages: OrderingTypes.Value = Value("Stages")
    val Fights: OrderingTypes.Value = Value("Fights")
    val Requirements: OrderingTypes.Value = Value("Requirements")
  }

  def findTopologicalOrdering(graph: Array[List[Int]], name: OrderingTypes.Value, inverse: Boolean = false): CanFail[Array[Int]] = {
    val n        = graph.length
    val inDegree = getIndegree(graph)
    val q        = mutable.Queue.empty[Int]
    for (i <- inDegree.indices; if inDegree(i) == 0) { q.enqueue(i) }
    var index    = 0
    val ordering = Array.fill(n)(0)
    while (q.nonEmpty) {
      val at = q.dequeue()
      if (inverse) {
        ordering(index) = at
      } else {
        ordering(at) = index
      }
      index += 1
      for (to <- graph(at)) {
        inDegree(to) -= 1
        if (inDegree(to) == 0) { q.enqueue(to) }
      }
    }
    if (index != n) { Left(Errors.InternalError(s"Graph either has cycles of is not connected. Graph for: ${name.toString}, Number of vertices = $n, TopSort length = $index")) }
    else { Right(ordering) }
  }
}
