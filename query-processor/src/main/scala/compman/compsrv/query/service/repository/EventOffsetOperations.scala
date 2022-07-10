package compman.compsrv.query.service.repository

import cats.Monad
import cats.effect.IO
import compman.compsrv.query.model.EventOffset
import org.mongodb.scala.MongoClient
import org.mongodb.scala.model.Filters.equal

object EventOffsetOperations {
  def test[F[_]: Monad]: EventOffsetService[F] = new EventOffsetService[F] {
    override def getOffset(topic: String): F[Option[EventOffset]] = Monad[F].pure(None)

    override def setOffset(offset: EventOffset): F[Unit] = Monad[F].pure(())

    override def deleteOffset(topic: String): F[Unit] = Monad[F].pure(())
  }
  def live(mongo: MongoClient, name: String): EventOffsetService[IO] =
    new EventOffsetService[IO] with CommonLiveOperations {

      override def mongoClient: MongoClient = mongo

      override def dbName: String = name

      override val idField = "topic"

      override def getOffset(topic: String): IO[Option[EventOffset]] = {
        for {
          collection <- eventOffsetCollection
          select = collection.find(equal(idField, topic))
          res <- selectOne(select)
        } yield res
      }

      override def deleteOffset(id: String): IO[Unit] = deleteById(eventOffsetCollection)(id)

      override def setOffset(offset: EventOffset): IO[Unit] = {
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
