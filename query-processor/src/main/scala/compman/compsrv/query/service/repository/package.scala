package compman.compsrv.query.service

import io.getquill.CassandraZioSession
import zio.{Has, RIO}
import zio.logging.Logging

package object repository {
  type QuillCassandraEnvironment = Has[CassandraZioSession]
  type RepoEnvironment           = QuillCassandraEnvironment with Logging
  type RepoIO[+A]                 = RIO[RepoEnvironment, A]
}
