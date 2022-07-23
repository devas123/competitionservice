package compman.compsrv.logic.actors

import cats.effect.IO
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import org.rocksdb.RocksDB

import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicReference

object SnapshotService {
  trait Service extends Serializable {
    def saveSnapshot(state: CommandProcessorCompetitionState): IO[Unit]
    def loadSnapshot(id: String): IO[Option[CommandProcessorCompetitionState]]
  }

  private final case class Live(rocksDB: RocksDB) extends Service {
    override def saveSnapshot(state: CommandProcessorCompetitionState): IO[Unit] =
      IO { rocksDB.put(state.id.getBytes, state.toByteArray) }

    override def loadSnapshot(id: String): IO[Option[CommandProcessorCompetitionState]] =
      IO { Option(rocksDB.get(id.getBytes)).map(bytes => CommandProcessorCompetitionState.parseFrom(bytes)) }

    private[actors] def close: IO[Unit] = IO(rocksDB.close())
  }

  private final case class Test(map: AtomicReference[Map[String, CommandProcessorCompetitionState]]) extends Service {
    override def saveSnapshot(state: CommandProcessorCompetitionState): IO[Unit] =
      IO { map.updateAndGet(_ + (state.id -> state)) }.void
    override def loadSnapshot(id: String): IO[Option[CommandProcessorCompetitionState]] = IO { map.get().get(id) }
  }

  def save(state: CommandProcessorCompetitionState)(implicit service: Service): IO[Unit] = service.saveSnapshot(state)
  def load(id: String)(implicit service: Service): IO[Option[CommandProcessorCompetitionState]] = service
    .loadSnapshot(id)

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
