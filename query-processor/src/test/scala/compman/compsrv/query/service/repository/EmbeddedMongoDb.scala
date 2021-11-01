package compman.compsrv.query.service.repository

import com.mongodb.connection.ClusterSettings
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.config.MongodbConfig
import de.flapdoodle.embed.mongo.config.Net
import org.mongodb.scala.{MongoClient, MongoClientSettings, ServerAddress}
import zio.{URIO, ZIO, ZManaged}

import scala.jdk.CollectionConverters._

trait EmbeddedMongoDb {

  import de.flapdoodle.embed.mongo.{MongodExecutable, MongodProcess, MongodStarter}
  import de.flapdoodle.embed.mongo.config.MongodConfig
  import de.flapdoodle.embed.mongo.distribution.Version
  import de.flapdoodle.embed.process.runtime.Network

  def startEmbeddedCassandra(): (MongodProcess, Int) = {
    val starter: MongodStarter = MongodStarter.getDefaultInstance

    val port: Int = 27018
    val mongodConfig: MongodConfig = MongodConfig.builder.version(Version.Main.PRODUCTION)
      .net(new Net(port, Network.localhostIsIPv6)).build

    var mongodExecutable: MongodExecutable = null
    mongodExecutable = starter.prepare(mongodConfig)
    (mongodExecutable.start, port)
  }

  def getCassandraResource: ZManaged[Any, Throwable, (MongodProcess, Int)] = ZManaged
    .make(ZIO.effect(startEmbeddedCassandra()))(c => URIO(c._1.stop()))
}

object EmbeddedMongoDb {
  implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]

  lazy val mongoClient: MongoClient = MongoClient("mongodb://localhost:27018")

  val mongodbConfig: MongodbConfig = MongodbConfig("localhost", 27018, "user", "password", "admin", "query_service")
  implicit val queryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
    .live(mongoClient, mongodbConfig.queryDatabaseName)
  implicit val updateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
    .live(mongoClient, mongodbConfig.queryDatabaseName)
}
