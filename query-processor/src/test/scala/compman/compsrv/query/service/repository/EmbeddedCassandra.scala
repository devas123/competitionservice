package compman.compsrv.query.service.repository

import com.datastax.oss.driver.api.core.CqlSession
import com.github.nosan.embedded.cassandra.{Cassandra, CassandraBuilder, DefaultWorkingDirectoryInitializer, WebCassandraDirectoryProvider}
import com.github.nosan.embedded.cassandra.commons.function.IOSupplier
import com.github.nosan.embedded.cassandra.cql.CqlScript

import java.net.InetSocketAddress
import java.nio.file.{Files, Path, Paths}
import scala.util.Using

trait EmbeddedCassandra {
  self =>

  self.startEmbeddedCassandra()

  def startEmbeddedCassandra(): Cassandra = {
    println("START \n\n\n\n\n")
    val cassandraDir = Files.createDirectories(Paths.get(".", "tmp", "cassandra"))
    val cassandra = new CassandraBuilder()
      .workingDirectory(new IOSupplier[Path]() {
      override def get(): Path = Files.createTempDirectory("apache-cassandra-embedded-")
    })
      .workingDirectoryInitializer(new DefaultWorkingDirectoryInitializer(new WebCassandraDirectoryProvider(cassandraDir),
        DefaultWorkingDirectoryInitializer.CopyStrategy.SKIP_EXISTING)).build()
    cassandra.start()
    val settings = cassandra.getSettings

    Using(
      CqlSession.builder().addContactPoint(new InetSocketAddress(settings.getAddress, settings.getPort()))
        .withLocalDatacenter("datacenter1").build()
    ) { connection => CqlScript.ofClassPath("/schema/schema.cql").forEachStatement(st => connection.execute(st)) }
    cassandra
  }

}
