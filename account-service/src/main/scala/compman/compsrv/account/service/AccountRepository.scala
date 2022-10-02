package compman.compsrv.account.service

import cats.effect.IO
import compman.compsrv.account.model.InternalAccount
import org.mongodb.scala.MongoClient

trait AccountRepository {
  def saveAccount(account: InternalAccount): IO[Unit]
  def deleteAccount(id: String): IO[Unit]
  def getAccountById(id: String): IO[Option[InternalAccount]]
  def getAccountByUserName(userName: String): IO[Option[InternalAccount]]
  def updateAccount(account: InternalAccount): IO[Unit]
}

object AccountRepository {
  def mongoAccountRepository(mongo: MongoClient, databaseName: String): AccountRepository =
    MongoAccountRepository(mongo, databaseName)
}
