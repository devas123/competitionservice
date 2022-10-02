package compman.compsrv.account.service

import cats.effect.IO
import compman.compsrv.account.model.InternalAccount

import scala.collection.mutable

case class InMemoryAccountRepository(accounts: mutable.Map[String, InternalAccount] = mutable.Map.empty)
    extends AccountRepository {
  override def saveAccount(account: InternalAccount): IO[Unit] = IO { accounts.put(account.userId, account) }.void

  override def deleteAccount(id: String): IO[Unit] = IO { accounts.remove(id) }.void

  override def getAccountById(id: String): IO[Option[InternalAccount]] = IO { accounts.get(id) }

  override def updateAccount(account: InternalAccount): IO[Unit] = saveAccount(account)

  override def getAccountByUserName(userName: String): IO[Option[InternalAccount]] =
    IO { accounts.find(_._2.email == userName).map(_._2) }
}
