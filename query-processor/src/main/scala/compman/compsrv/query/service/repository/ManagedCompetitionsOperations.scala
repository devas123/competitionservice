package compman.compsrv.query.service.repository

import cats.Monad
import cats.effect.IO
import cats.implicits.toFunctorOps
import compman.compsrv.query.model.{CompetitionProperties, CompetitionState, ManagedCompetition, RegistrationInfo}
import compservice.model.protobuf.model.CompetitionStatus
import org.mongodb.scala.MongoClient
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Filters.not

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.Date

object ManagedCompetitionsOperations {
  def test[F[+_]: Monad](competitions: AtomicReference[Map[String, ManagedCompetition]]): ManagedCompetitionService[F] =
    new ManagedCompetitionService[F] {
      override def getManagedCompetitions: F[List[ManagedCompetition]] = {
        Monad[F].pure(competitions.get().values.toList)
      }

      override def getActiveCompetitions: F[List[ManagedCompetition]] = getManagedCompetitions

      override def addManagedCompetition(competition: ManagedCompetition): F[Unit] = {
        Monad[F].pure(competitions.updateAndGet(m => m + (competition.id -> competition))).void
      }

      override def competitionExists(id: String): F[Boolean] = Monad[F].pure(competitions.get().contains(id))
    }

  def live(mongo: MongoClient, name: String): ManagedCompetitionService[IO] = new ManagedCompetitionService[IO]
    with CommonLiveOperations {
    import org.mongodb.scala.model.Projections._

    private val managedCompetitionProjection = include(
      "id",
      "competitionName",
      "eventsTopic",
      "properties.creatorId",
      "properties.creationTimestamp",
      "properties.startDate",
      "properties.endDate",
      "properties.timeZone",
      "properties.status"
    )

    override def mongoClient: MongoClient = mongo

    override def dbName: String = name

    override def getManagedCompetitions: IO[List[ManagedCompetition]] = {

      for {
        collection <- managedCompetitionCollection
        select = collection.find().projection(managedCompetitionProjection)
        res <- runQuery(select).map(_.map(decodeBson))
      } yield res
    }

    override def getActiveCompetitions: IO[List[ManagedCompetition]] = {
      for {
        collection <- managedCompetitionCollection
        select = collection.find(not(Filters.eq("properties.status", CompetitionStatus.DELETED)))
          .projection(managedCompetitionProjection)
        res <- runQuery(select).map(_.map(decodeBson))
      } yield res
    }
    override def addManagedCompetition(competition: ManagedCompetition): IO[Unit] = {
      val state = CompetitionState(
        id = Some(competition.id),
        eventsTopic = Some(competition.eventsTopic),
        properties = Some(CompetitionProperties(
          id = competition.id,
          creatorId = competition.creatorId.getOrElse(""),
          staffIds = None,
          competitionName = competition.competitionName.getOrElse(""),
          startDate = Date.from(competition.startDate),
          schedulePublished = false,
          bracketsPublished = false,
          endDate = competition.endDate.map(Date.from),
          timeZone = competition.timeZone,
          creationTimestamp = Date.from(competition.creationTimestamp),
          status = competition.status
        )),
        periods = Some(Map.empty),
        categories = Some(Map.empty),
        stages = Some(Map.empty),
        registrationInfo = Some(RegistrationInfo(
          id = competition.id,
          registrationGroups = Map.empty,
          registrationPeriods = Map.empty,
          registrationOpen = false
        ))
      )
      insertElement(competitionStateCollection)(competition.id, state)
    }

    override def competitionExists(id: String): IO[Boolean] = for {
      collection <- managedCompetitionCollection
      select = collection.countDocuments(
        Filters.and(not(Filters.eq("properties.status", CompetitionStatus.DELETED)), Filters.eq(idField, id))
      ).map(_ > 0)
      res <- IO.fromFuture(IO(select.toFuture())).map(_.headOption.getOrElse(false))
    } yield res
  }

  private def decodeBson(document: BsonDocument) = {
    val properties = document.get("properties").asDocument()
    ManagedCompetition(
      id = document.get("id").asString().getValue,
      competitionName = getOptionalString(properties, "competitionName"),
      eventsTopic = document.get("eventsTopic").asString().getValue,
      creatorId = getOptionalString(properties, "creatorId"),
      startDate = Instant.ofEpochMilli(properties.get("startDate").asDateTime().getValue),
      creationTimestamp = Instant.ofEpochMilli(properties.get("creationTimestamp").asDateTime().getValue),
      endDate = getOptionalDate(properties),
      timeZone = properties.get("timeZone").asString().getValue,
      status = CompetitionStatus.fromValue(properties.get("status").asString().getValue.toInt)
    )
  }

  private def getOptionalString(document: BsonDocument, propertyName: String) = {
    if (document.containsKey(propertyName)) { Option(document.get(propertyName).asString().getValue) }
    else { None }
  }

  private def getOptionalDate(document: BsonDocument): Option[Instant] = {
    if (document.containsKey("endDate")) { Option(Instant.ofEpochMilli(document.get("endDate").asDateTime().getValue)) }
    else { None }
  }

  trait ManagedCompetitionService[F[+_]] {
    def getManagedCompetitions: F[List[ManagedCompetition]]
    def getActiveCompetitions: F[List[ManagedCompetition]]
    def addManagedCompetition(competition: ManagedCompetition): F[Unit]
    def competitionExists(id: String): F[Boolean]
  }

  object ManagedCompetitionService {
    def apply[F[+_]](implicit F: ManagedCompetitionService[F]): ManagedCompetitionService[F] = F
  }
  def getActiveCompetitions[F[+_]: ManagedCompetitionService]: F[List[ManagedCompetition]] =
    ManagedCompetitionService[F].getActiveCompetitions
  def addManagedCompetition[F[+_]: ManagedCompetitionService](competition: ManagedCompetition): F[Unit] =
    ManagedCompetitionService[F].addManagedCompetition(competition)
  def competitionExists[F[+_]: ManagedCompetitionService](competitionId: String): F[Boolean] =
    ManagedCompetitionService[F].competitionExists(competitionId)
}
