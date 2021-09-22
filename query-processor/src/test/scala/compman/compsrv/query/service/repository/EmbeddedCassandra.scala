package compman.compsrv.query.service.repository

import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory
import com.github.nosan.embedded.cassandra.api.connection.DefaultCassandraConnectionFactory
import com.github.nosan.embedded.cassandra.api.cql.CqlDataSet
import com.github.nosan.embedded.cassandra.api.Cassandra

import scala.util.Using

trait EmbeddedCassandra { self =>

  self.startEmbeddedCassandra()

  def startEmbeddedCassandra(): Cassandra = {
    println("START \n\n\n\n\n")
    val cassandraFactory = new EmbeddedCassandraFactory()
    val cassandra        = cassandraFactory.create()
    cassandra.start()
    val cassandraConnectionFactory = new DefaultCassandraConnectionFactory()
    Using(cassandraConnectionFactory.create(cassandra)) { connection =>
      CqlDataSet.ofClasspaths("/schema/schema.cql").forEachStatement(connection.execute)
    }
    cassandra
  }

}
