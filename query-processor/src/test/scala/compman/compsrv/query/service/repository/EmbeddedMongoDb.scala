package compman.compsrv.query.service.repository

import cats.effect.IO
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.SpecBase
import org.mongodb.scala.MongoClient
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

import java.io.IOException
import java.net.{InetAddress, ServerSocket}
import java.time.Duration

trait EmbeddedMongoDb {
  self: SpecBase =>

  @throws[IOException]
  def freeServerPort(hostAddress: InetAddress): Int = {
    val socket = new ServerSocket(0, 0, hostAddress)
    try socket.getLocalPort
    finally if (socket != null) socket.close()
  }

  def embeddedMongo(): MongoDBContainer = {
    val mongoPort = 27017
    val container = new MongoDBContainer(DockerImageName.parse("mongo:latest")).withExposedPorts(mongoPort)
      .withStartupTimeout(Duration.ofSeconds(30))
    container.start()
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      container.stop()
      ()
    }))
    container
  }
}

object EmbeddedMongoDb {

  class EmbeddedMongoContext(port: Int) {
    lazy val mongoClient: MongoClient = MongoClient(s"mongodb://localhost:$port")
    val mongodbConfig: MongodbConfig  = MongodbConfig("localhost", port, "user", "password", "admin", "query_service")
    implicit val queryOperations: CompetitionQueryOperations[IO] = CompetitionQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val updateOperations: CompetitionUpdateOperations[IO] = CompetitionUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightQueryOperations: FightQueryOperations[IO] = FightQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightUpdateOperations: FightUpdateOperations[IO] = FightUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val managedCompetitionsOperations: ManagedCompetitionsOperations.ManagedCompetitionService[IO] =
      ManagedCompetitionsOperations.live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  def context(port: Int) = new EmbeddedMongoContext(port)
}
