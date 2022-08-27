package compman.compsrv.account.service

import cats.effect.IO
import com.mongodb.client.model.{IndexOptions, ReplaceOptions}
import compman.compsrv.account.model.InternalAccount
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase, Observable}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Indexes}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.{set, unset}
import scalapb.{GeneratedEnum, GeneratedEnumCompanion}

import scala.reflect.ClassTag

trait AccountServiceMongoLiveOperations extends AccountServiceMongoCommonFields {

  private final val accountCollectionName = "account"

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

  private val caseClassRegistry = fromRegistries(fromProviders(classOf[InternalAccount]), DEFAULT_CODEC_REGISTRY)

  def mongoClient: MongoClient
  def dbName: String

  private def createCollection[T: ClassTag](name: String, id: String): IO[MongoCollection[T]] = for {
    collection <- IO { database.getCollection[T](name) }
    _ <- IO.fromFuture(IO(collection.createIndex(Indexes.ascending(id), new IndexOptions().unique(true)).toFuture()))
  } yield collection

  val database: MongoDatabase = mongoClient.getDatabase(dbName).withCodecRegistry(fromRegistries(caseClassRegistry))
  def accountCollection: IO[MongoCollection[InternalAccount]] =
    createCollection[InternalAccount](accountCollectionName, idField).memoize.flatten

  protected def setOption[T](name: String, opt: Option[T]): Bson = opt.map(v => set(name, v)).getOrElse(unset(name))
  protected def deleteByField[T](
    collectionTask: IO[MongoCollection[T]]
  )(id: String, idField: String = idField): IO[Unit] = {
    for {
      collection <- collectionTask
      delete = collection.deleteMany(equal(idField, id))
      _ <- IO.fromFuture(IO(delete.toFuture()))
    } yield ()
  }
  protected def insertElement[T](collectionTask: IO[MongoCollection[T]])(id: String, element: T): IO[Unit] = {
    for {
      collection <- collectionTask
      insert = collection.replaceOne(Filters.eq(idField, id), element, new ReplaceOptions().upsert(true))
      _ <- IO.fromFuture(IO(insert.toFuture()))
    } yield ()
  }

  def cd[T <: GeneratedEnum: ClassTag](clazz: Class[T], companion: GeneratedEnumCompanion[T]): Seq[Codec[T]] = {
    companion.values.map(v => new EnumCodec(v.getClass.asInstanceOf[Class[T]], companion).asInstanceOf[Codec[T]]) :+
      new EnumCodec[T](clazz, companion)
  }

  class EnumCodec[T <: GeneratedEnum: ClassTag](val clazz: Class[T], companion: GeneratedEnumCompanion[T])
      extends Codec[T] {
    override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit = {
      writer.writeString(value.value.toString)
    }
    override def getEncoderClass: Class[T] = clazz
    override def decode(reader: BsonReader, decoderContext: DecoderContext): T = {
      companion.fromValue(reader.readString().toInt)
    }
  }

  protected def selectOne[T](select: Observable[T]): IO[Option[T]] = {
    for { res <- runQuery(select) } yield res.headOption
  }

  protected def runQuery[T](select: Observable[T]): IO[List[T]] = {
    for { res <- IO.fromFuture(IO(select.toFuture())) } yield res.toList
  }
}
