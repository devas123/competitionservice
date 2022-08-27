package compman.compsrv.account.service

import cats.effect.IO
import compman.compsrv.account.model.InternalAccount
import org.mongodb.scala.MongoClient
import org.mongodb.scala.model.Filters

trait AccountRepository {
  def saveAccount(account: InternalAccount): IO[Unit]
  def deleteAccount(id: String): IO[Unit]
  def getAccount(id: String): IO[Option[InternalAccount]]
  def updateAccount(account: InternalAccount): IO[Unit]
}

object AccountRepository {
  def mongoAccountRepository(mongo: MongoClient, databaseName: String): AccountRepository = new AccountRepository
    with AccountServiceMongoLiveOperations {
    override def saveAccount(account: InternalAccount): IO[Unit] = for {
      collection <- accountCollection
      insert = collection.insertOne(account)
      _ <- IO.fromFuture(IO(insert.toFuture()))
    } yield ()

    override def deleteAccount(id: String): IO[Unit] = for {
      collection <- accountCollection
      delete = collection.deleteOne(Filters.eq(idField, id))
      _ <- IO.fromFuture(IO(delete.toFuture()))
    } yield ()

    override def getAccount(id: String): IO[Option[InternalAccount]] = for {
      collection <- accountCollection
      select = collection.find(Filters.eq(idField, id))
      res <- selectOne(select)
    } yield res

    override def updateAccount(account: InternalAccount): IO[Unit] = for {
      collection <- accountCollection
      update = collection.replaceOne(Filters.eq(idField, account.userId), account)
      _ <- IO.fromFuture(IO(update.toFuture()))
    } yield ()

    override def mongoClient: MongoClient = mongo

    override def dbName: String = databaseName
  }
}
