package compman.compsrv.account.config

import com.typesafe.config.Config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

final case class MongodbConfig(
                                host: String,
                                port: Int,
                                username: String,
                                password: String,
                                authenticationDb: String,
                                accountDatabaseName: String
)

case class AccountServiceConfig(mongo: MongodbConfig, version: String, requestTimeout: FiniteDuration)
object AccountServiceConfig {
  def load(config: Config): AccountServiceConfig = AccountServiceConfig(
    mongo = MongodbConfig(
      host = config.getString("mongo.host"),
      port = config.getInt("mongo.port"),
      username = config.getString("mongo.username"),
      password = config.getString("mongo.password"),
      authenticationDb = config.getString("mongo.authenticationDb"),
      accountDatabaseName = config.getString("mongo.accountDatabaseName")
    ),
    version = config.getString("version"),
    requestTimeout = FiniteDuration(config.getDuration("requestTimeout").toMillis, TimeUnit.MILLISECONDS)
  )
}
