package compman.compsrv.logic.service.schedule

import compman.compsrv.logic.service.generate.CanFail
import compman.compsrv.model.Errors

import scala.collection.mutable

object GraphUtils {
  def getIndegree(graph: List[Set[Int]]): List[Int] = {
    val inDegree = Array.fill(graph.size)(0)
    for {
      adj <- graph
      it  <- adj
    } { inDegree(it) += 1 }
    inDegree.toList
  }

  def findTopologicalOrdering(graph: List[Set[Int]], inverse: Boolean = false): CanFail[List[Int]] = {
    val n        = graph.size
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
    else { Right(ordering.toList) }
  }
}
