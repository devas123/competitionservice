package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.config.MongodbConfig
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.MongodProcess
import org.mongodb.scala.MongoClient
import zio.{Task, URIO}

trait EmbeddedMongoDb {

  import de.flapdoodle.embed.mongo.{MongodExecutable, MongodStarter}
  import de.flapdoodle.embed.mongo.config.MongodConfig
  import de.flapdoodle.embed.mongo.distribution.Version
  import de.flapdoodle.embed.process.runtime.Network

  def startEmbeddedMongo(): (MongodProcess, Int) = {
    val starter: MongodStarter = MongodStarter.getDefaultInstance
    val mongodConfig: MongodConfig = MongodConfig.builder.version(Version.Main.PRODUCTION)
      .net(new Net(EmbeddedMongoDb.port, Network.localhostIsIPv6)).build

    var mongodExecutable: MongodExecutable = null
    mongodExecutable = starter.prepare(mongodConfig)
    val start = mongodExecutable.start
    while (!start.isProcessRunning) { Thread.sleep(100) }
    (start, EmbeddedMongoDb.port)
  }

  def stopServer(mongodProcess: MongodProcess): Task[Unit] = {
    URIO(println("\n\n\nStopping server")) *> URIO(mongodProcess.stop())
  }
}

object EmbeddedMongoDb {
  implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]

  val port = 27018

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
}
