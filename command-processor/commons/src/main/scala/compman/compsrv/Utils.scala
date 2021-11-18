package compman.compsrv

object Utils {
  def groupById[T, K](collection: Iterable[T])(id: T => K): Map[K, T] = collection.groupMapReduce(id)(identity)((a, _) => a)
}
