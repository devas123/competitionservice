package compman.compsrv.query.service.repository

import cats.Monad
import cats.effect.IO
import com.mongodb.client.model.{ReplaceOptions, UpdateOptions}
import compman.compsrv.query.model._
import compservice.model.protobuf.model.StageStatus
import org.mongodb.scala.{Document, MongoClient}
import org.mongodb.scala.model.{Filters, Updates}

import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters._

trait CompetitionUpdateOperations[F[+_]] {
  def updateCompetitionProperties(competitionProperties: CompetitionProperties): F[Unit]
  def removeCompetitionState(id: String): F[Unit]
  def addCompetitionInfoTemplate(competitionId: String)(competitionInfoTemplate: Array[Byte]): F[Unit]
  def removeCompetitionInfoTemplate(competitionId: String): F[Unit]
  def addCompetitionInfoImage(competitionId: String)(competitionInfoImage: Array[Byte]): F[Unit]
  def removeCompetitionInfoImage(competitionId: String): F[Unit]
  def addStage(stageDescriptor: StageDescriptor): F[Unit]
  def updateStage(stageDescriptor: StageDescriptor): F[Unit]
  def removeStages(competition: String)(ids: Set[String]): F[Unit]
  def updateStageStatus(competitionId: String)(categoryId: String, stageId: String, newStatus: StageStatus): F[Unit]
  def addCategory(category: Category): F[Unit]
  def updateCategoryRegistrationStatus(competitionId: String)(id: String, newStatus: Boolean): F[Unit]
  def removeCategory(competitionId: String)(id: String): F[Unit]
  def addCompetitor(competitor: Competitor): F[Unit]
  def updateCompetitor(competitor: Competitor): F[Unit]
  def removeCompetitor(competitionId: String)(id: String): F[Unit]
  def removeCompetitorsForCompetition(competitionId: String): F[Unit]
  def removeCompetitorsForCategory(competitionId: String)(categoryId: String): F[Unit]
  def updateRegistrationInfo(competitionId: String)(registrationInfo: RegistrationInfo): F[Unit]
  def addPeriod(entry: Period): F[Unit]
  def addPeriods(entries: List[Period]): F[Unit]
  def updatePeriods(entries: List[Period]): F[Unit]
  def removePeriod(competitionId: String)(id: String): F[Unit]
  def removePeriods(competitionId: String): F[Unit]
}

object CompetitionUpdateOperations {
  def apply[F[+_]](implicit F: CompetitionUpdateOperations[F]): CompetitionUpdateOperations[F] = F

  import cats.implicits._

  def test[F[+_]: Monad](
    competitionProperties: Option[AtomicReference[Map[String, CompetitionProperties]]] = None,
    registrationInfo: Option[AtomicReference[Map[String, RegistrationInfo]]] = None,
    categories: Option[AtomicReference[Map[String, Category]]] = None,
    competitors: Option[AtomicReference[Map[String, Competitor]]] = None,
    periods: Option[AtomicReference[Map[String, Period]]] = None,
    stages: Option[AtomicReference[Map[String, StageDescriptor]]] = None
  ): CompetitionUpdateOperations[F] = new CompetitionUpdateOperations[F] with CommonTestOperations {

    def addCompetitionProperties(newProperties: CompetitionProperties): F[Unit] = Monad[F]
      .pure(competitionProperties.foreach(_.updateAndGet(m => m.updated(newProperties.id, newProperties))))

    override def updateCompetitionProperties(competitionProperties: CompetitionProperties): F[Unit] =
      addCompetitionProperties(competitionProperties)

    override def removeCompetitionState(id: String): F[Unit] = Monad[F]
      .pure(competitionProperties.foreach(_.updateAndGet(m => m - id)))

    override def addCompetitionInfoTemplate(competitionId: String)(newTemplate: Array[Byte]): F[Unit] =
      comPropsUpdate[F](competitionProperties)(competitionId)(identity)

    override def removeCompetitionInfoTemplate(competitionId: String): F[Unit] =
      comPropsUpdate[F](competitionProperties)(competitionId)(identity)

    override def addStage(stageDescriptor: StageDescriptor): F[Unit] =
      add[F, StageDescriptor](stages)(stageDescriptor.id)(Some(stageDescriptor))

    override def updateStage(stageDescriptor: StageDescriptor): F[Unit] =
      stagesUpdate[F](stages)(stageDescriptor.id)(_ => stageDescriptor)

    override def updateStageStatus(
      competitionId: String
    )(categoryId: String, stageId: String, newStatus: StageStatus): F[Unit] =
      stagesUpdate[F](stages)(stageId)(_.copy(stageStatus = newStatus))

    override def addCategory(category: Category): F[Unit] = add[F, Category](categories)(category.id)(Some(category))

    override def updateCategoryRegistrationStatus(competitionId: String)(id: String, newStatus: Boolean): F[Unit] =
      update[F, Category](categories)(id)(_.copy(registrationOpen = newStatus))

    override def removeCategory(competitionId: String)(id: String): F[Unit] = remove[F, Category](categories)(id)

    override def addCompetitor(competitor: Competitor): F[Unit] =
      add[F, Competitor](competitors)(competitor.id)(Some(competitor))

    override def updateCompetitor(competitor: Competitor): F[Unit] =
      update[F, Competitor](competitors)(competitor.id)(_ => competitor)

    override def removeCompetitor(competitionId: String)(id: String): F[Unit] = remove[F, Competitor](competitors)(id)

    override def updateRegistrationInfo(competitionId: String)(info: RegistrationInfo): F[Unit] =
      update[F, RegistrationInfo](registrationInfo)(competitionId)(_ => info)

    override def addPeriod(entry: Period): F[Unit] = add[F, Period](periods)(entry.id)(Some(entry))

    override def addPeriods(entries: List[Period]): F[Unit] = entries.traverse(addPeriod).map(_ => ())

    override def updatePeriods(entries: List[Period]): F[Unit] = entries
      .traverse(e => update[F, Period](periods)(e.id)(_ => e)).map(_ => ())

    override def removePeriod(competitionId: String)(id: String): F[Unit] = remove[F, Period](periods)(id)

    override def removePeriods(competitionId: String): F[Unit] = Monad[F]
      .pure(periods.foreach(_.updateAndGet(m => m.filter { case (_, p) => p.competitionId == competitionId })))

    override def removeStages(competition: String)(ids: Set[String]): F[Unit] = Monad[F]
      .pure(stages.foreach(_.updateAndGet(s => s.filter(s => !ids.contains(s._2.id)))))

    override def removeCompetitorsForCompetition(competitionId: String): F[Unit] = Monad[F]
      .pure(competitors.foreach(_.updateAndGet(c => c.filter(_._2.competitionId != competitionId))))

    override def removeCompetitorsForCategory(competitionId: String)(categoryId: String): F[Unit] = Monad[F].pure(
      competitors.map(_.updateAndGet(c => c.map(e => (e._1, e._2.copy(categories = e._2.categories - categoryId)))))
    ).void

    override def addCompetitionInfoImage(competitionId: String)(competitionInfoImage: Array[Byte]): F[Unit] =
      addCompetitionInfoTemplate(competitionId)(competitionInfoImage)

    override def removeCompetitionInfoImage(competitionId: String): F[Unit] =
      removeCompetitionInfoTemplate(competitionId)
  }

  def live(mongo: MongoClient, name: String): CompetitionUpdateOperations[IO] = new CompetitionUpdateOperations[IO]
    with CommonLiveOperations {

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Updates._

    override def updateCompetitionProperties(competitionProperties: CompetitionProperties): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection
          .findOneAndUpdate(equal(idField, competitionProperties.id), set("properties", competitionProperties))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeCompetitionState(id: String): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection.deleteMany(equal(idField, id))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def addCompetitionInfoTemplate(competitionId: String)(competitionInfoTemplate: Array[Byte]): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection
          .findOneAndUpdate(equal(idField, competitionId), set("info.template", competitionInfoTemplate))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeCompetitionInfoTemplate(competitionId: String): IO[Unit] = for {
      collection <- competitionStateCollection
      statement = collection.findOneAndUpdate(equal(idField, competitionId), unset("info.template"))
      _ <- IO.fromFuture(IO(statement.toFuture()))
    } yield ()

    override def addStage(stageDescriptor: StageDescriptor): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection.findOneAndUpdate(
          equal(idField, stageDescriptor.competitionId),
          set(s"stages.${stageDescriptor.id}", stageDescriptor)
        )
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def updateStage(stageDescriptor: StageDescriptor): IO[Unit] = addStage(stageDescriptor)

    override def removeStages(competition: String)(ids: Set[String]): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        updates = ids.map(id => unset(s"stages.$id")).toSeq
        _ <-
          if (ids.nonEmpty) IO
            .fromFuture(IO(collection.findOneAndUpdate(equal(idField, competition), combine(updates: _*)).toFuture()))
          else IO.unit
      } yield ()
    }

    override def updateStageStatus(
      competitionId: String
    )(categoryId: String, stageId: String, newStatus: StageStatus): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection
          .findOneAndUpdate(equal(idField, competitionId), set(s"stages.$stageId.stageStatus", newStatus))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def addCategory(category: Category): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection
          .findOneAndUpdate(equal(idField, category.competitionId), set(s"categories.${category.id}", category))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def updateCategoryRegistrationStatus(competitionId: String)(id: String, newStatus: Boolean): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection
          .findOneAndUpdate(equal(idField, competitionId), set(s"categories.$id.registrationOpen", newStatus))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeCategory(competitionId: String)(id: String): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection.findOneAndUpdate(equal(idField, competitionId), unset(s"categories.$id"))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def addCompetitor(competitor: Competitor): IO[Unit] = {
      for {
        collection <- competitorCollection
        statement = collection
          .replaceOne(Filters.eq(idField, competitor.id), competitor, new ReplaceOptions().upsert(true))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def updateCompetitor(competitor: Competitor): IO[Unit] = {
      for {
        collection <- competitorCollection
        statement = collection.replaceOne(equal(idField, competitor.id), competitor)
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeCompetitor(competitionId: String)(id: String): IO[Unit] = {
      for {
        collection <- competitorCollection
        statement = collection.deleteOne(equal(idField, id))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeCompetitorsForCompetition(competitionId: String): IO[Unit] = {
      for {
        collection <- competitorCollection
        statement = collection.deleteMany(equal(competitionIdField, competitionId))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removeCompetitorsForCategory(competitionId: String)(categoryId: String): IO[Unit] = {
      for {
        collection <- competitorCollection
        statement = collection.updateMany(
          and(equal(competitionIdField, competitionId), equal("categories", categoryId)),
          Updates.unset("categories.$[element]"),
          new UpdateOptions().arrayFilters(List(Document("element" -> Document("$eq" -> categoryId))).asJava)
        )
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def updateRegistrationInfo(competitionId: String)(registrationInfo: RegistrationInfo): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection
          .findOneAndUpdate(equal(idField, competitionId), set(s"registrationInfo", registrationInfo))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def addPeriod(entry: Period): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection.findOneAndUpdate(equal(idField, entry.competitionId), set(s"periods.${entry.id}", entry))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def addPeriods(entries: List[Period]): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        updates   = entries.map(period => set(s"periods.${period.id}", period))
        statement = collection.findOneAndUpdate(equal(idField, entries.head.competitionId), combine(updates: _*))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def updatePeriods(entries: List[Period]): IO[Unit] = addPeriods(entries)

    override def removePeriod(competitionId: String)(id: String): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection.findOneAndUpdate(equal(idField, competitionId), unset(s"periods.$id"))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def removePeriods(competitionId: String): IO[Unit] = {
      for {
        collection <- competitionStateCollection
        statement = collection.findOneAndUpdate(equal(idField, competitionId), set(s"periods", Document()))
        _ <- IO.fromFuture(IO(statement.toFuture()))
      } yield ()
    }

    override def addCompetitionInfoImage(competitionId: String)(competitionInfoImage: Array[Byte]): IO[Unit] = for {
      collection <- competitionStateCollection
      statement = collection.findOneAndUpdate(equal(idField, competitionId), set("info.image", competitionInfoImage))
      _ <- IO.fromFuture(IO(statement.toFuture()))
    } yield ()

    override def removeCompetitionInfoImage(competitionId: String): IO[Unit] = for {
      collection <- competitionStateCollection
      statement = collection.findOneAndUpdate(equal(idField, competitionId), unset("info.image"))
      _ <- IO.fromFuture(IO(statement.toFuture()))
    } yield ()

  }
}
