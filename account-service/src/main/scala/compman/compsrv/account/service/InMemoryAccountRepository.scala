package compman.compsrv.account.service

import cats.effect.IO
import compman.compsrv.account.model.InternalAccount

import scala.collection.mutable

case class InMemoryAccountRepository(accounts: mutable.Map[String, InternalAccount] = mutable.Map.empty)
    extends AccountRepository {
  override def saveAccount(account: InternalAccount): IO[Unit] = IO { accounts.put(account.userId, account) }.void

  override def deleteAccount(id: String): IO[Unit] = IO { accounts.remove(id) }.void

  override def getAccount(id: String): IO[Option[InternalAccount]] = IO { accounts.get(id) }

  override def updateAccount(account: InternalAccount): IO[Unit] = saveAccount(account)
}
