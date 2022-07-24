package compman.compsrv.query.service.repository

import cats.Monad
import compman.compsrv.query.model.{CompetitionProperties, StageDescriptor}

import java.util.concurrent.atomic.AtomicReference

trait CommonTestOperations {
  def getById[F[_]: Monad, T](map: Option[AtomicReference[Map[String, T]]])(id: String): F[Option[T]] = map match {
    case Some(value) => Monad[F].pure(value.get.get(id))
    case None        => Monad[F].pure(None)
  }
  def getStagesByCategory[F[_]: Monad](
    stages: Option[AtomicReference[Map[String, StageDescriptor]]]
  )(competitionId: String)(categoryId: String): F[List[StageDescriptor]] = stages match {
    case Some(value) => Monad[F].pure(value.get.values.toList.filter(_.categoryId == categoryId))
    case None        => Monad[F].pure(List.empty)
  }

  def update[F[_]: Monad, T](coll: Option[AtomicReference[Map[String, T]]])(id: String)(u: T => T): F[Unit] = Monad[F]
    .pure(coll.foreach(_.updateAndGet(m => m.updatedWith(id)(optComp => optComp.map(u)))))

  def add[F[_]: Monad, T](coll: Option[AtomicReference[Map[String, T]]])(id: String)(a: => Option[T]): F[Unit] =
    Monad[F].pure(coll.foreach(_.updateAndGet(m => m.updatedWith(id)(_ => a))))

  def remove[F[_]: Monad, T](coll: Option[AtomicReference[Map[String, T]]])(id: String): F[Unit] = Monad[F]
    .pure(coll.foreach(_.updateAndGet(m => m - id)))

  def comPropsUpdate[F[_]: Monad](competitionProperties: Option[AtomicReference[Map[String, CompetitionProperties]]])(
    competitionId: String
  )(u: CompetitionProperties => CompetitionProperties): F[Unit] = { update(competitionProperties)(competitionId)(u) }

  def stagesUpdate[F[_]: Monad](stages: Option[AtomicReference[Map[String, StageDescriptor]]])(stageId: String)(
    u: StageDescriptor => StageDescriptor
  ): F[Unit] = { update(stages)(stageId)(u) }

}
