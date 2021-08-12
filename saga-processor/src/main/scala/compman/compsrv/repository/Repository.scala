package compman.compsrv.repository

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jackson.ObjectMapperFactory
import compman.compsrv.model.{CompetitionState, CompetitionStateImpl}
import org.rocksdb.RocksDB
import zio.Task


trait CompetitionStateCrudRepository[F[+_]] {
  def add(entity: CompetitionState): F[Unit]
  def remove(id: String): F[Unit]
  def get(id: String): F[CompetitionState]
  def exists(id: String): F[Boolean]
  def close(): F[Unit]
}

object CompetitionStateCrudRepository {
  def createLive(rdb: RocksDB): CompetitionStateCrudRepository[Task] = RocksDbCompetitionStateRepository(rdb)
}

private[repository] final case class RocksDbCompetitionStateRepository(rdb: RocksDB)
    extends CompetitionStateCrudRepository[Task] {
  val objectMapper: ObjectMapper = ObjectMapperFactory.createObjectMapper

  override def add(entity: CompetitionState): Task[Unit] = Task {
    rdb.put(entity.id.getBytes, objectMapper.writeValueAsBytes(entity))
  }

  override def remove(id: String): Task[Unit] = Task {
    rdb.delete(id.getBytes)
  }

  override def get(id: String): Task[CompetitionState] =
    for {
      bytes <- Task {
        rdb.get(id.getBytes)
      }
    } yield objectMapper.createParser(bytes).readValueAs(classOf[CompetitionStateImpl])

  override def exists(id: String): Task[Boolean] = Task {
    rdb.get(id.getBytes) != null
  }

  override def close(): Task[Unit] = Task {
    rdb.close()
  }
}