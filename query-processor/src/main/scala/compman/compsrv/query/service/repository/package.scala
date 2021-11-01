package compman.compsrv.query.service

import zio.RIO
import zio.logging.Logging

package object repository {
  type RepoEnvironment           =  Logging
  type RepoIO[+A]                 = RIO[RepoEnvironment, A]
}
