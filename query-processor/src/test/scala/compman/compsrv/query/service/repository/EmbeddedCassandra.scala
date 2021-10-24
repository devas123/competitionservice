package compman.compsrv.query.service.repository

import com.datastax.oss.driver.api.core.CqlSession
import com.github.nosan.embedded.cassandra.commons.FileSystemResource
import com.github.nosan.embedded.cassandra.commons.function.IOSupplier
import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.github.nosan.embedded.cassandra.{Cassandra, CassandraBuilder, DefaultWorkingDirectoryInitializer, WebCassandraDirectoryProvider}
import zio.duration.durationInt
import zio.{URIO, ZIO, ZManaged}

import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}
import scala.util.Using
import scala.util.Using.Releasable

trait EmbeddedCassandra {
  private implicit val rel: Releasable[Cassandra] = (resource: Cassandra) => resource.stop()

  def withCassandra[T](block: () => T): T = {
    Using(createCassandra()) { _ =>
      block()
    }.get
  }


  def getCassandraResource: ZManaged[Any, Throwable, Cassandra] = ZManaged.make(startEmbeddedCassandra())(c => URIO(c.stop()))

  private def createCassandra() = {
    val cassandraDir = Files.createDirectories(Paths.get(".", "tmp", "cassandra"))
    val cassandra = new CassandraBuilder()
      .addEnvironmentVariable("JAVA_HOME", System.getProperty("JAVA_HOME"))
      .jvmOptions("-XX:-UseBiasedLocking", "-XX:-UseConcMarkSweepGC")
      .workingDirectory(new IOSupplier[Path]() {
        override def get(): Path = Files.createTempDirectory("apache-cassandra-embedded-")
      })
      .workingDirectoryInitializer(new DefaultWorkingDirectoryInitializer(new WebCassandraDirectoryProvider(cassandraDir),
        DefaultWorkingDirectoryInitializer.CopyStrategy.SKIP_EXISTING)).build()
    cassandra.start()
    cassandra
  }

  def startEmbeddedCassandra(): ZIO[Any, Throwable, Cassandra] = for {
    _ <- ZIO.effect(println(s"${Paths.get("query-processor/cassandra/schema.cql").toAbsolutePath.toString} \n\n\n\n\n"))
    cassandraDir = Files.createDirectories(Paths.get(".", "tmp", "cassandra"))
    cassandra = new CassandraBuilder()
      .addEnvironmentVariable("JAVA_HOME", System.getProperty("JAVA_HOME"))
      .jvmOptions("-XX:-UseBiasedLocking", "-XX:-UseConcMarkSweepGC")
      .workingDirectory(new IOSupplier[Path]() {
        override def get(): Path = Files.createTempDirectory("apache-cassandra-embedded-")
      })
      .workingDirectoryInitializer(new DefaultWorkingDirectoryInitializer(new WebCassandraDirectoryProvider(cassandraDir),
        DefaultWorkingDirectoryInitializer.CopyStrategy.SKIP_EXISTING)).build()
    _ <- ZIO.effect(cassandra.start())
    _ <- ZIO.effect {
      while (!cassandra.isRunning) {
        ZIO.effect(println("Waiting for cassandra to start")) *> ZIO.sleep(100.millis)
      }
    }
    settings = cassandra.getSettings
    _ <- ZIO.effect {
      Using(
        CqlSession.builder().addContactPoint(new InetSocketAddress(settings.getAddress, settings.getPort()))
          .withLocalDatacenter("datacenter1").build()
      ) { connection => CqlScript.ofResource(new FileSystemResource(Paths.get("query-processor/cassandra/schema.cql"))).forEachStatement(st => connection.execute(st)) }
    }
  } yield cassandra

}
