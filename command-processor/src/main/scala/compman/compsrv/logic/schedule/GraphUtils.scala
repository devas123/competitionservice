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

  def findTopologicalOrdering(graph: Array[List[Int]], inverse: Boolean = false): CanFail[Array[Int]] = {
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
    if (index != n) { Left(Errors.InternalError("Cycles in the graph.")) }
    else { Right(ordering) }
  }
}
