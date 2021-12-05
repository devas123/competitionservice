package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.config.MongodbConfig
import org.mongodb.scala.MongoClient
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import zio.test.DefaultRunnableSpec
import zio.{ZIO, ZManaged}

import java.io.IOException
import java.net.{InetAddress, ServerSocket}
import java.time.Duration

trait EmbeddedMongoDb {
  self: DefaultRunnableSpec =>

  @throws[IOException]
  def freeServerPort(hostAddress: InetAddress): Int = {
    val socket = new ServerSocket(0, 0, hostAddress)
    try socket.getLocalPort
    finally if (socket != null) socket.close()
  }

  def embeddedMongo(): ZManaged[Any, Nothing, MongoDBContainer] = {
    ZManaged.make(ZIO.effectTotal {
      val mongoPort = 27017
      val container = new MongoDBContainer(DockerImageName.parse("mongo:latest")).withExposedPorts(mongoPort)
        .withStartupTimeout(Duration.ofSeconds(30))
      container.start()
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        container.stop()
        ()
      }))
      container
    })(container => ZIO.effectTotal(container.stop()))
  }
}

object EmbeddedMongoDb {
  implicit val logging: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]

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

  def context(port: Int) = new EmbeddedMongoContext(port)
}
