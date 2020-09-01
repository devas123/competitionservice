package compman.compsrv.service.schedule

import java.util.*

object GraphUtils {

    fun getIndegree(graph: Array<MutableSet<Int>>): IntArray {
        val inDegree = IntArray(graph.size) { 0 }
        graph.forEach { adj -> adj.forEach { inDegree[it]++ } }
        return inDegree
    }

    fun findTopologicalOrdering(graph: Array<MutableSet<Int>>, inverse: Boolean = false): IntArray {
        val n = graph.size
        val inDegree = getIndegree(graph)
        val q = LinkedList<Int>()
        for (i in inDegree.indices) {
            if (inDegree[i] == 0) {
                q.offer(i)
            }
        }
        var index = 0
        val ordering = IntArray(n) { 0 }
        while (!q.isEmpty()) {
            val at = q.poll()
            if (inverse) {
                ordering[index++] = at
            } else {
                ordering[at] = index++
            }
            for (to in graph[at]) {
                if (--inDegree[to] == 0) {
                    q.offer(to)
                }
            }
        }
        if (index != n) {
            error("Cycles in graph.")
        }
        return ordering
    }
}