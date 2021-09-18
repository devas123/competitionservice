package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model.ManagedCompetition
import io.getquill.{CassandraZioContext, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.{Has, Tag, ZIO, ZLayer}

object ManagedCompetitionsOperations {
  type Service[R] = Has[ManagedCompetitionService[R]]

  def live(implicit log: CompetitionLogging.Service[LIO]): ZLayer[Any, Throwable, Service[RepoEnvironment]] = ZLayer
    .succeed {
      new ManagedCompetitionService[RepoEnvironment] {
        private lazy val ctx =
          new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders
        import ctx._
        override def getManagedCompetitions: ZIO[RepoEnvironment, Throwable, List[ManagedCompetition]] = {
          val select = quote { query[ManagedCompetition] }
          for {
            _   <- log.info(select.toString)
            res <- run(select)
          } yield res
        }
        override def addManagedCompetition(competition: ManagedCompetition): ZIO[RepoEnvironment, Throwable, Unit] = {
          val insert = quote { query[ManagedCompetition].insert(liftCaseClass(competition)) }
          for {
            _ <- log.info(insert.toString)
            _ <- run(insert)
          } yield ()
        }

        override def deleteManagedCompetition(id: String): ZIO[RepoEnvironment, Throwable, Unit] = {
          val delete = quote { query[ManagedCompetition].filter(_.competitionId == lift(id)).delete }
          for {
            _ <- log.info(delete.toString)
            _ <- run(delete)
          } yield ()
        }
      }
    }

  trait ManagedCompetitionService[R] {
    def getManagedCompetitions: ZIO[R, Throwable, List[ManagedCompetition]]
    def addManagedCompetition(competition: ManagedCompetition): ZIO[R, Throwable, Unit]
    def deleteManagedCompetition(id: String): ZIO[R, Throwable, Unit]
  }

  def getManagedCompetitions[R: Tag]: ZIO[R with Service[R], Throwable, List[ManagedCompetition]] = ZIO
    .accessM(_.get[ManagedCompetitionService[R]].getManagedCompetitions)
  def addManagedCompetition[R: Tag](competition: ManagedCompetition): ZIO[R with Service[R], Throwable, Unit] = ZIO
    .accessM(_.get[ManagedCompetitionService[R]].addManagedCompetition(competition))
  def deleteManagedCompetition[R: Tag](id: String): ZIO[R with Service[R], Throwable, Unit] = ZIO
    .accessM(_.get[ManagedCompetitionService[R]].deleteManagedCompetition(id))

}
