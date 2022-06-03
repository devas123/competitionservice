package compman.compsrv.logic.actors

import compservice.model.protobuf.model.CommandProcessorCompetitionState
import org.rocksdb.RocksDB
import zio.{Has, Ref, Task, UIO, URIO, ZIO, ZManaged}

import java.nio.file.{Files, Paths}

object SnapshotService {
  type Snapshot = Has[Service]
  trait Service extends Serializable {
    def saveSnapshot(state: CommandProcessorCompetitionState): URIO[Snapshot, Unit]
    def loadSnapshot(id: String): URIO[Snapshot, Option[CommandProcessorCompetitionState]]
  }

  private final case class Live(rocksDB: RocksDB) extends Service {
    override def saveSnapshot(state: CommandProcessorCompetitionState): URIO[Snapshot, Unit] =
      URIO { rocksDB.put(state.id.getBytes, state.toByteArray) }

    override def loadSnapshot(id: String): URIO[Snapshot, Option[CommandProcessorCompetitionState]] =
      URIO { Option(rocksDB.get(id.getBytes)).map(bytes => CommandProcessorCompetitionState.parseFrom(bytes)) }

    private[actors] def close: UIO[Unit] = UIO(rocksDB.close())
  }

  private final case class Test(map: Ref[Map[String, CommandProcessorCompetitionState]]) extends Service {
    override def saveSnapshot(state: CommandProcessorCompetitionState): URIO[Snapshot, Unit] = for {
      _ <- map.update(_ + (state.id -> state))
    } yield ()

    override def loadSnapshot(id: String): URIO[Snapshot, Option[CommandProcessorCompetitionState]] = for {
      m <- map.get
    } yield m.get(id)
  }

  def save(state: CommandProcessorCompetitionState): URIO[Snapshot, Unit] = ZIO.accessM(_.get.saveSnapshot(state))
  def load(id: String): URIO[Snapshot, Option[CommandProcessorCompetitionState]] = ZIO.accessM(_.get.loadSnapshot(id))

  def live(path: String): ZManaged[Any, Throwable, Service] = {
    (for {
      _ <- Task {
        val p = Paths.get(path)
        if (!Files.exists(p)) { Files.createDirectories(p) }
      }
      db <- ZIO.effect(RocksDB.open(path))
    } yield Live(rocksDB = db)).toManaged(_.close)
  }
  def test: ZManaged[Any, Nothing, Service] = {
    (for {
      map <- Ref.make(Map.empty[String, CommandProcessorCompetitionState])
      r   <- ZIO.effectTotal(Test(map))
    } yield r).toManaged(_ => ZIO.unit)
  }
}
