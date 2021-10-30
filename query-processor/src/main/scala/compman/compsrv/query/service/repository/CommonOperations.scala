package compman.compsrv.query.service.repository

import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model.{CompetitionProperties, StageDescriptor}
import zio.{IO, Ref, Task, ZIO}

trait CommonOperations {
  def getById[T](map: Option[Ref[Map[String, T]]])(id: String): Task[Option[T]] = map match {
    case Some(value) => value.get.map(_.get(id))
    case None        => Task(None)
  }
  def getStagesByCategory(
    stages: Option[Ref[Map[String, StageDescriptor]]]
  )(competitionId: String)(categoryId: String): LIO[List[StageDescriptor]] = stages match {
    case Some(value) => value.get.map(_.values.toList.filter(_.categoryId == categoryId))
    case None        => Task(List.empty)
  }

  def update[T](coll: Option[Ref[Map[String, T]]])(id: String)(u: T => T): IO[Nothing, Unit] = coll
    .map(_.update(m => m.updatedWith(id)(optComp => optComp.map(u)))).getOrElse(ZIO.unit)

  def add[T](coll: Option[Ref[Map[String, T]]])(id: String)(a: => Option[T]): IO[Nothing, Unit] = coll
    .map(_.update(m => m.updatedWith(id)(_ => a))).getOrElse(ZIO.unit)

  def remove[T](coll: Option[Ref[Map[String, T]]])(id: String): IO[Nothing, Unit] = coll.map(_.update(m => m - id)).getOrElse(ZIO.unit)

  def comPropsUpdate(competitionProperties: Option[Ref[Map[String, CompetitionProperties]]])(competitionId: String)(
    u: CompetitionProperties => CompetitionProperties
  ): IO[Nothing, Unit] = { update(competitionProperties)(competitionId)(u) }

  def stagesUpdate(stages: Option[Ref[Map[String, StageDescriptor]]])(stageId: String)(
    u: StageDescriptor => StageDescriptor
  ): IO[Nothing, Unit] = { update(stages)(stageId)(u) }

}
