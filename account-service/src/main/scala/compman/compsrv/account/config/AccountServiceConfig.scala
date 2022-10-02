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

final case class AuthenticationConfig(jwtSecretKey: String)

final case class AccountServiceConfig(
  mongo: MongodbConfig,
  version: String,
  requestTimeout: FiniteDuration,
  port: Int,
  authentication: AuthenticationConfig
)
object AccountServiceConfig {
  def load(globalConfig: Config): AccountServiceConfig = {
    val accountConfig = globalConfig.getConfig("account")
    AccountServiceConfig(
      mongo = MongodbConfig(
        host = accountConfig.getString("mongo.host"),
        port = accountConfig.getInt("mongo.port"),
        username = accountConfig.getString("mongo.username"),
        password = accountConfig.getString("mongo.password"),
        authenticationDb = accountConfig.getString("mongo.authenticationDb"),
        accountDatabaseName = accountConfig.getString("mongo.accountDatabaseName")
      ),
      version = accountConfig.getString("version"),
      port = accountConfig.getInt("port"),
      requestTimeout = FiniteDuration(accountConfig.getDuration("requestTimeout").toMillis, TimeUnit.MILLISECONDS),
      authentication = AuthenticationConfig(accountConfig.getString("authentication.jwt-secret-key"))
    )
  }
}
