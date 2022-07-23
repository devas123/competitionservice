package compman.compsrv.logic.actors

import compservice.model.protobuf.model.CommandProcessorCompetitionState
import org.rocksdb.RocksDB

import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicReference

object SnapshotService {
  trait Service extends Serializable {
    def saveSnapshot(state: CommandProcessorCompetitionState): Unit
    def loadSnapshot(id: String): Option[CommandProcessorCompetitionState]
    def close(): Unit
  }

  private final case class Live(rocksDB: RocksDB) extends Service {
    override def saveSnapshot(state: CommandProcessorCompetitionState): Unit = rocksDB
      .put(state.id.getBytes, state.toByteArray)

    override def loadSnapshot(id: String): Option[CommandProcessorCompetitionState] = Option(rocksDB.get(id.getBytes))
      .map(bytes => CommandProcessorCompetitionState.parseFrom(bytes))

    override def close(): Unit = rocksDB.close()
  }

  private final case class Test(map: AtomicReference[Map[String, CommandProcessorCompetitionState]]) extends Service {
    override def saveSnapshot(state: CommandProcessorCompetitionState): Unit = {
      map.updateAndGet(_ + (state.id -> state))
      ()
    }

    override def loadSnapshot(id: String): Option[CommandProcessorCompetitionState] = map.get().get(id)
    override def close(): Unit                                                      = ()
  }

  def save(state: CommandProcessorCompetitionState)(implicit service: Service): Unit = service.saveSnapshot(state)
  def load(id: String)(implicit service: Service): Option[CommandProcessorCompetitionState] = service.loadSnapshot(id)

  def live(path: String): Service = {
    val p = Paths.get(path)
    if (!Files.exists(p)) { Files.createDirectories(p) }
    val db = RocksDB.open(path)
    Live(rocksDB = db)
  }
  def test: Service = {
    val map = new AtomicReference(Map.empty[String, CommandProcessorCompetitionState])
    Test(map)
  }
}
