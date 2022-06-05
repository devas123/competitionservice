package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model.EventOffset
import org.mongodb.scala.MongoClient
import org.mongodb.scala.model.Filters.equal
import zio.RIO

object EventOffsetOperations {
  def test: EventOffsetService[LIO] = new EventOffsetService[LIO] {
    override def getOffset(topic: String): LIO[Option[EventOffset]] = RIO.none

    override def setOffset(offset: EventOffset): LIO[Unit] = RIO.unit

    override def deleteOffset(topic: String): LIO[Unit] = RIO.unit
  }
  def live(mongo: MongoClient, name: String): EventOffsetService[LIO] = new EventOffsetService[LIO]
    with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override val idField = "topic"

    override def getOffset(topic: String): LIO[Option[EventOffset]] = {
      for {
        collection <- eventOffsetCollection
        select = collection.find(equal(idField, topic))
        res <- selectOne(select)
      } yield res
    }

    override def deleteOffset(id: String): LIO[Unit] = deleteById(eventOffsetCollection)(id)

    override def setOffset(offset: EventOffset): LIO[Unit] = {
      insertElement(eventOffsetCollection)(offset.topic, offset)
    }
  }

  trait EventOffsetService[F[+_]] {
    def getOffset(topic: String): F[Option[EventOffset]]
    def setOffset(offset: EventOffset): F[Unit]
    def deleteOffset(topic: String): F[Unit]
  }

  object EventOffsetService {
    def apply[F[+_]](implicit F: EventOffsetService[F]): EventOffsetService[F] = F
  }

  def getOffset[F[+_]: EventOffsetService](topic: String): F[Option[EventOffset]] = EventOffsetService[F]
    .getOffset(topic)
  def setOffset[F[+_]: EventOffsetService](offset: EventOffset): F[Unit] = EventOffsetService[F].setOffset(offset)
  def deleteOffset[F[+_]: EventOffsetService](topic: String): F[Unit]    = EventOffsetService[F].deleteOffset(topic)
}
