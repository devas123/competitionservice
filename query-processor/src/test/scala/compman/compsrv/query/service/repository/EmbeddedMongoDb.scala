package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.config.MongodbConfig
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.MongodProcess
import org.mongodb.scala.MongoClient
import zio.{Task, ZIO}
import zio.test.DefaultRunnableSpec

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import scala.util.Using

trait EmbeddedMongoDb {
  self: DefaultRunnableSpec =>
  import de.flapdoodle.embed.mongo.{MongodExecutable, MongodStarter}
  import de.flapdoodle.embed.mongo.config.MongodConfig
  import de.flapdoodle.embed.mongo.distribution.Version
  import de.flapdoodle.embed.process.runtime.Network

  def startEmbeddedMongo(): (MongodProcess, Int) = {
    EmbeddedMongoDb.mongoProcess.updateAndGet(pair =>
      if (pair == null) {
        val starter: MongodStarter = MongodStarter.getDefaultInstance
        val freePort               = Network.freeServerPort(InetAddress.getLoopbackAddress)
        val mongodConfig: MongodConfig = MongodConfig.builder.version(Version.Main.PRODUCTION)
          .net(new Net(freePort, Network.localhostIsIPv6)).build

        var mongodExecutable: MongodExecutable = null
        mongodExecutable = starter.prepare(mongodConfig)
        val start = mongodExecutable.start
        while (!start.isProcessRunning) { Thread.sleep(100) }

        Using(MongoClient(s"mongo://localhost:$freePort")) { mongo =>
          mongo.getDatabase("query_service").drop().subscribe(_ => (), _ => ())
        }
        (start, freePort)
      } else pair
    )
  }

  def stopServer(): Task[Unit] = {
    ZIO.effect {
      EmbeddedMongoDb.mongoProcess.updateAndGet { process =>
        process._1.stop()
        null
      }
    }.as(())
  }
}

object EmbeddedMongoDb {
  implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]

  private val mongoProcess = new AtomicReference[(MongodProcess, Int)]()

  class EmbeddedMongoContext(port: Int) {
    lazy val mongoClient: MongoClient = MongoClient(s"mongodb://localhost:$port")
    val mongodbConfig: MongodbConfig  = MongodbConfig("localhost", port, "user", "password", "admin", "query_service")
    implicit val queryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val updateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)

    implicit val fightQueryOperations: FightQueryOperations[LIO] = FightQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightUpdateOperations: FightUpdateOperations[LIO] = FightUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val managedCompetitionsOperations: ManagedCompetitionsOperations.ManagedCompetitionService[LIO] =
      ManagedCompetitionsOperations.live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  def context = new EmbeddedMongoContext(mongoProcess.get()._2)
}
