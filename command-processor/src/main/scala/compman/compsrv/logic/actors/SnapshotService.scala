package compman.compsrv.logic.actors

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jackson.ObjectMapperFactory
import compman.compsrv.logic.CompetitionState
import org.rocksdb.RocksDB
import zio.{Has, Ref, Task, UIO, URIO, ZIO, ZManaged}

import java.nio.file.{Files, Paths}

object SnapshotService {
  type Snapshot = Has[Service]
  trait Service extends Serializable {
    def saveSnapshot(state: CompetitionState): URIO[Snapshot, Unit]
    def loadSnapshot(id: String): URIO[Snapshot, Option[CompetitionState]]
  }

  private final case class Live(rocksDB: RocksDB, mapper: ObjectMapper) extends Service {
    override def saveSnapshot(state: CompetitionState): URIO[Snapshot, Unit] =
      URIO { rocksDB.put(state.id.getBytes, mapper.writeValueAsBytes(state)) }

    override def loadSnapshot(id: String): URIO[Snapshot, Option[CompetitionState]] =
      URIO { Option(rocksDB.get(id.getBytes)).map(bytes => mapper.readValue(bytes, classOf[CompetitionState])) }

    private[actors] def close: UIO[Unit] = UIO(rocksDB.close())
  }

  private final case class Test(map: Ref[Map[String, CompetitionState]]) extends Service {
    override def saveSnapshot(state: CompetitionState): URIO[Snapshot, Unit] = for {
      _ <- map.update(_ + (state.id -> state))
    } yield ()

    override def loadSnapshot(id: String): URIO[Snapshot, Option[CompetitionState]] = for {
      m <- map.get
    } yield m.get(id)
  }

  def save(state: CompetitionState): URIO[Snapshot, Unit]        = ZIO.accessM(_.get.saveSnapshot(state))
  def load(id: String): URIO[Snapshot, Option[CompetitionState]] = ZIO.accessM(_.get.loadSnapshot(id))

  def live(path: String): ZManaged[Any, Throwable, Service] = {
    (for {
      _ <- Task {
        val p = Paths.get(path)
        if (!Files.exists(p)) { Files.createDirectories(p) }
      }
      db     <- ZIO.effect(RocksDB.open(path))
      mapper <- ZIO.effectTotal(ObjectMapperFactory.createObjectMapper)
    } yield Live(rocksDB = db, mapper = mapper)).toManaged(_.close)
  }
  def test: ZManaged[Any, Nothing, Service] = {
    (for {
      map <- Ref.make(Map.empty[String, CompetitionState])
      r   <- ZIO.effectTotal(Test(map))
    } yield r).toManaged(_ => ZIO.unit)
  }
}
