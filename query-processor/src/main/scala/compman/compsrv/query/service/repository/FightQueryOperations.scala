package compman.compsrv.query.service.repository

import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import io.getquill.{CassandraZioContext, CassandraZioSession, SnakeCase}
import io.getquill.context.cassandra.encoding.{Decoders, Encoders}
import zio.{Has, Ref, Task}
import zio.interop.catz._

trait FightQueryOperations[F[+_]] {
  def getFightsByScheduleEntries(competitionId: String): F[List[FightByScheduleEntry]]
  def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): F[Int]
  def getNumberOfFightsForMat(competitionId: String)(matId: String): F[Int]
  def getFightsByMat(competitionId: String)(matId: String, limit: Int): F[List[Fight]]
  def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): F[List[Fight]]

  def getFightById(competitionId: String)(categoryId: String, id: String): F[Option[Fight]]
  def getFightIdsByCategoryIds(competitionId: String): F[Map[String, List[String]]]
  def getFightsByIds(competitionId: String)(categoryId: String, ids: Set[String]): F[List[Fight]]
}

object FightQueryOperations {
  def apply[F[+_]](implicit F: FightQueryOperations[F]): FightQueryOperations[F] = F

  def test(
    fights: Option[Ref[Map[String, Fight]]] = None,
    stages: Option[Ref[Map[String, StageDescriptor]]] = None
  ): FightQueryOperations[LIO] = new FightQueryOperations[LIO] with CommonOperations {
    override def getFightsByMat(competitionId: String)(matId: String, limit: Int): LIO[List[Fight]] = fights match {
      case Some(value) => value.get
          .map(_.values.filter(f => f.competitionId == competitionId && f.matId.contains(matId)).toList)
      case None => Task(List.empty)
    }
    override def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): LIO[List[Fight]] =
      fights match {
        case Some(value) => value.get
            .map(_.values.filter(f => f.competitionId == competitionId && f.stageId == stageId).toList)
        case None => Task(List.empty)
      }
    override def getFightById(competitionId: String)(categoryId: String, id: String): LIO[Option[Fight]] =
      getById(fights)(id)
    override def getFightsByIds(competitionId: String)(categoryId: String, ids: Set[String]): LIO[List[Fight]] = ids
      .toList.traverse(getById(fights)).map(_.mapFilter(identity))

    override def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): LIO[Int] = for {
      stages      <- getStagesByCategory(stages)(competitionId)(categoryId)
      stageFights <- stages.traverse(s => getFightsByStage(competitionId)(categoryId, s.id))
    } yield stageFights.flatten.size

    override def getFightIdsByCategoryIds(competitionId: String): LIO[Map[String, List[String]]] = fights match {
      case Some(value) => value.get.map(
          _.values.filter(f => f.competitionId == competitionId).map(f => (f.categoryId, f.id)).groupMap(_._1)(_._2)
            .view.mapValues(_.toList).toMap
        )
      case None => Task(Map.empty)
    }

    override def getFightsByScheduleEntries(competitionId: String): LIO[List[FightByScheduleEntry]] = fights
      .map(_.get.map(_.filter(_._2.competitionId == competitionId).map(e => {
        val scheduleEntry = e._2.scheduleEntryId.getOrElse("")
        FightByScheduleEntry(
          categoryId = e._2.categoryId,
          scheduleEntryId = scheduleEntry,
          competitionId = competitionId,
          matId = e._2.matId,
          fightId = e._2.id,
          startTime = e._2.startTime,
          periodId = e._2.periodId.getOrElse("")
        )
      }).toList)).getOrElse(Task(List.empty))

    override def getNumberOfFightsForMat(competitionId: String)(matId: String): LIO[Int] = for {
      compStages <- stages match {
        case Some(value) => value.get.map(_.values.toList)
        case None        => Task(List.empty)
      }
      stageFights <- compStages.traverse(s => getFightsByStage(competitionId)(s.categoryId, s.id))
    } yield stageFights.flatten.size

  }

  def live(
    cassandraZioSession: CassandraZioSession
  )(implicit log: CompetitionLogging.Service[LIO]): FightQueryOperations[LIO] = new FightQueryOperations[LIO] {
    private lazy val ctx =
      new CassandraZioContext(SnakeCase) with CustomDecoders with CustomEncoders with Encoders with Decoders

    import ctx._

    private val fightsByMat = quote { (competitionId: String, matId: String) =>
      querySchema[Fight]("fight_by_mat").filter(f => f.competitionId == competitionId && f.matId.contains(matId))
    }

    private val fightsByStage = quote { (competitionId: String, categoryId: String, stageId: String) =>
      query[Fight].filter(f => f.competitionId == competitionId && f.categoryId == categoryId && f.stageId == stageId)
    }

    private val fightById = quote { (competitionId: String, categoryId: String, id: String) =>
      query[Fight].filter(f => f.competitionId == competitionId && f.categoryId == categoryId && f.id == id).take(1)
    }

    override def getFightsByMat(competitionId: String)(matId: String, limit: Int): LIO[List[Fight]] = {
      val select = quote { fightsByMat(lift(competitionId), lift(matId)).take(lift(limit)).allowFiltering }

      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getFightsByStage(competitionId: String)(categoryId: String, stageId: String): LIO[List[Fight]] = {
      val select = quote { fightsByStage(lift(competitionId), lift(categoryId), lift(stageId)).allowFiltering }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getFightById(competitionId: String)(categoryId: String, id: String): LIO[Option[Fight]] = {
      val select = quote { fightById(lift(competitionId), lift(categoryId), lift(id)) }

      for {
        _ <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession)).map(_.headOption)
      } yield res
    }

    override def getFightsByIds(competitionId: String)(categoryId: String, ids: Set[String]): LIO[List[Fight]] = {
      val select = quote(query[Fight]).dynamic.filterIf(ids.nonEmpty)(f =>
        quote(
          f.competitionId == lift(competitionId) && f.categoryId == lift(categoryId) && liftQuery(ids).contains(f.id)
        )
      )
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res
    }

    override def getNumberOfFightsForCategory(competitionId: String)(categoryId: String): LIO[Int] = {
      val select = quote {
        query[Fight].filter(f => f.competitionId == lift(competitionId) && f.categoryId == lift(categoryId)).size
      }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res.toInt

    }

    override def getNumberOfFightsForMat(competitionId: String)(matId: String): LIO[Int] = {
      val select = quote {
        query[Fight].filter(f => f.competitionId == lift(competitionId) && f.matId.contains(lift(matId))).map(_.id)
          .allowFiltering
      }
      for {
        _   <- log.info(select.toString)
        res <- run(quote(select)).provide(Has(cassandraZioSession))
      } yield res.size
    }

    override def getFightIdsByCategoryIds(competitionId: String): LIO[Map[String, List[String]]] = {
      val select =
        quote { query[Fight].filter(f => f.competitionId == lift(competitionId)).map(f => (f.categoryId, f.id)) }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res.groupMap(_._1)(_._2)
    }

    override def getFightsByScheduleEntries(competitionId: String): LIO[List[FightByScheduleEntry]] = {
      val select = quote {
        query[Fight].filter(f => f.competitionId == lift(competitionId))
          .map(f => (f.scheduleEntryId, f.categoryId, f.periodId, f.competitionId, f.matId, f.id, f.startTime))
      }
      for {
        _   <- log.info(select.toString)
        res <- run(select).provide(Has(cassandraZioSession))
      } yield res.filter(t => t._1.isDefined && t._3.isDefined)
        .map(t => (t._1.getOrElse(""), t._2, t._3.getOrElse(""), t._4, t._5, t._6, t._7))
        .map(FightByScheduleEntry.tupled)
    }
  }
}
