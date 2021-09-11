package compman.compsrv.query.service.repository

import compman.compsrv.query.model.ManagedCompetition
import io.getquill.{CassandraZioContext, CassandraZioSession, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.{Has, Tag, ZIO}

object ManagedCompetitions {
  type Service[R]                = Has[ManagedCompetitionService[R]]
  type QuillCassandraEnvironment = Has[CassandraZioSession]

  val live: ManagedCompetitionService[QuillCassandraEnvironment] =
    new ManagedCompetitionService[QuillCassandraEnvironment] {
      private lazy val ctx = new CassandraZioContext(SnakeCase)  with CustomDecoders with CustomEncoders with Encoders with Decoders
      import ctx._
      override def getManagedCompetitions: ZIO[QuillCassandraEnvironment, Throwable, List[ManagedCompetition]] = {
        run(query[ManagedCompetition]).map(_.toList)
      }
      override def addManagedCompetition(
        competition: ManagedCompetition
      ): ZIO[QuillCassandraEnvironment, Throwable, Unit] = {
        implicit val meta: ctx.InsertMeta[ManagedCompetition] = insertMeta[ManagedCompetition]()
        run(query[ManagedCompetition].insert(liftCaseClass(competition))).ignore
      }

      override def deleteManagedCompetition(id: String): ZIO[QuillCassandraEnvironment, Throwable, Unit] = {
        run(query[ManagedCompetition].delete.ifCond(_.competitionId == lift(id))).ignore
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
