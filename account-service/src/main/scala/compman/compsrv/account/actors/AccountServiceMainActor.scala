package compman.compsrv.account.actors

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import com.mongodb.connection.ClusterSettings
import compman.compsrv.account.config.AccountServiceConfig
import compman.compsrv.account.service.{AccountRepository, HttpServer}
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
    val bindingFuture = HttpServer.runServer(config, accountRepoSupervisor)
    Behaviors.receiveSignal[AccountServiceMainActorApi] { case (_, PostStop) =>
      bindingFuture.flatMap(_.unbind())
      Behaviors.same
    }

  }

}
