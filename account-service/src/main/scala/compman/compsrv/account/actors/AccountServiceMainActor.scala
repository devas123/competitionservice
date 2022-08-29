package compman.compsrv.account.actors

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.{ExitCode, IO}
import com.mongodb.connection.ClusterSettings
import compman.compsrv.account.config.AccountServiceConfig
import compman.compsrv.account.service.{AccountRepository, HttpServer}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential, ServerAddress}

import scala.concurrent.ExecutionContextExecutor
import scala.jdk.CollectionConverters._

object AccountServiceMainActor {
  sealed trait AccountServiceMainActorApi
  def behavior(): Behavior[AccountServiceMainActorApi] = Behaviors.setup { ctx =>
    implicit val system: ActorSystem[_]       = ctx.system
    implicit val ex: ExecutionContextExecutor = system.executionContext
    val config                                = AccountServiceConfig.load(ctx.system.settings.config)

    val mongodbConfig = config.mongo
    val credential = MongoCredential
      .createCredential(mongodbConfig.username, mongodbConfig.authenticationDb, mongodbConfig.password.toCharArray)

    val mongodbSession = MongoClient(
      MongoClientSettings.builder().credential(credential)
        .applyToClusterSettings((builder: ClusterSettings.Builder) => {
          builder.hosts(List(new ServerAddress(mongodbConfig.host)).asJava)
          ()
        }).build()
    )

    val accountRepository = AccountRepository.mongoAccountRepository(mongodbSession, mongodbConfig.accountDatabaseName)

    val accountRepoSupervisor = ctx.spawn(
      AccountRepositorySupervisorActor.behavior(accountRepository, config.requestTimeout),
      "accountRepositorySupervisor"
    )

    val prefix  = s"/accountsrv/${config.version}"
    val httpApp = Router[IO](prefix -> HttpServer.routes(accountRepoSupervisor)).orNotFound

    val bindingFuture = BlazeServerBuilder[IO](ex).bindHttp(config.port, "localhost").withHttpApp(httpApp)
      .serve
      .compile.drain
      .as(ExitCode.Success)

    ctx.spawn(AccountHttpServiceRunner.behavior(bindingFuture), "httpServer")

    ctx.log.info(s"Started server at http://localhost:${config.port}$prefix")
    Behaviors.receiveSignal[AccountServiceMainActorApi] { case (_, PostStop) => Behaviors.same }
  }
}
