package compman.compsrv.query.service.repository

import cats.effect.IO
import cats.Monad
import cats.implicits.toFunctorOps
import com.mongodb.client.model.UpdateOptions
import compman.compsrv.query.model.BlobWithId
import org.mongodb.scala.{MongoClient, MongoCollection}
import org.mongodb.scala.model.{Filters, Updates}

import scala.collection.mutable

object BlobOperations {

  def test[F[+_]: Monad]: BlobService[F] = new BlobService[F] {
    val images: mutable.Map[String, Array[Byte]]                         = mutable.Map.empty
    val infos: mutable.Map[String, Array[Byte]]                          = mutable.Map.empty
    override def getCompetitionImage(id: String): F[Option[Array[Byte]]] = Monad[F].pure(images.get(id))

    override def getCompetitionInfo(id: String): F[Option[Array[Byte]]] = Monad[F].pure(infos.get(id))

    override def saveCompetitionImage(id: String, blob: Array[Byte]): F[Unit] = Monad[F].pure(images.put(id, blob)).void

    override def saveCompetitionInfo(id: String, blob: Array[Byte]): F[Unit] = Monad[F].pure(infos.put(id, blob)).void

    override def deleteCompetitionImage(id: String): F[Unit] = Monad[F].pure(infos.remove(id)).void

    override def deleteCompetitionInfo(id: String): F[Unit] = Monad[F].pure(infos.remove(id)).void
  }
  def live(mongo: MongoClient, name: String): BlobService[IO] = new BlobService[IO] with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    private def getBlobFromCollection(coll: MongoCollection[BlobWithId], id: String) =
      selectOne(coll.find(Filters.eq(idField, id))).map(_.flatMap(_.blob))
    private def saveBlobToCollection(coll: MongoCollection[BlobWithId], id: String, blob: Array[Byte]) = IO
      .fromFuture(IO(
        coll.updateOne(Filters.eq(idField, id), Updates.set("blob", blob), new UpdateOptions().upsert(true)).toFuture()
      ))
    private def deleteBlobFromCollection(coll: MongoCollection[BlobWithId], id: String) = IO
      .fromFuture(IO(coll.deleteOne(Filters.eq(idField, id)).toFuture()))

    override def getCompetitionImage(id: String): IO[Option[Array[Byte]]] = for {
      collection <- competitionImageBlobCollection
      res        <- getBlobFromCollection(collection, id)
    } yield res

    override def getCompetitionInfo(id: String): IO[Option[Array[Byte]]] = for {
      collection <- competitionInfoBlobCollection
      res        <- getBlobFromCollection(collection, id)
    } yield res

    override def saveCompetitionImage(id: String, blob: Array[Byte]): IO[Unit] = for {
      collection <- competitionImageBlobCollection
      _          <- saveBlobToCollection(collection, id, blob)
    } yield ()

    override def saveCompetitionInfo(id: String, blob: Array[Byte]): IO[Unit] = for {
      collection <- competitionInfoBlobCollection
      _          <- saveBlobToCollection(collection, id, blob)
    } yield ()

    override def deleteCompetitionImage(id: String): IO[Unit] = for {
      collection <- competitionImageBlobCollection
      _          <- deleteBlobFromCollection(collection, id)
    } yield ()

    override def deleteCompetitionInfo(id: String): IO[Unit] = for {
      collection <- competitionInfoBlobCollection
      _          <- deleteBlobFromCollection(collection, id)
    } yield ()

  }

  trait BlobService[F[+_]] {
    def getCompetitionImage(id: String): F[Option[Array[Byte]]]
    def getCompetitionInfo(id: String): F[Option[Array[Byte]]]
    def saveCompetitionImage(id: String, blob: Array[Byte]): F[Unit]
    def saveCompetitionInfo(id: String, blob: Array[Byte]): F[Unit]
    def deleteCompetitionImage(id: String): F[Unit]
    def deleteCompetitionInfo(id: String): F[Unit]
  }

  object BlobService {
    def apply[F[+_]](implicit F: BlobService[F]): BlobService[F] = F
  }

  def getCompetitionImage[F[+_]: BlobService](id: String): F[Option[Array[Byte]]] = BlobService[F]
    .getCompetitionImage(id)
  def getCompetitionInfo[F[+_]: BlobService](id: String): F[Option[Array[Byte]]] = BlobService[F].getCompetitionInfo(id)
  def saveCompetitionImage[F[+_]: BlobService](id: String, blob: Array[Byte]): F[Unit] = BlobService[F]
    .saveCompetitionImage(id, blob)
  def saveCompetitionInfo[F[+_]: BlobService](id: String, blob: Array[Byte]): F[Unit] = BlobService[F]
    .saveCompetitionInfo(id, blob)
  def deleteCompetitionImage[F[+_]: BlobService](id: String): F[Unit] = BlobService[F].deleteCompetitionImage(id)
  def deleteCompetitionInfo[F[+_]: BlobService](id: String): F[Unit]  = BlobService[F].deleteCompetitionInfo(id)
}
