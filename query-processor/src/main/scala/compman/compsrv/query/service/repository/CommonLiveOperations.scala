package compman.compsrv.query.service.repository

import com.mongodb.client.model.{IndexOptions, ReplaceOptions}
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import compman.compsrv.query.model.academy.FullAcademyInfo
import compservice.model.protobuf.model.{FightStatus,
  CompetitorRegistrationStatus,
  CompetitionStatus,
  DistributionType,
  StageRoundType,
  GroupSortDirection,
  LogicalOperator,
  GroupSortSpecifier,
  SelectorClassifier,
  OperatorType,
  BracketType,
  StageType,
  StageStatus,
  CategoryRestrictionType,
  FightReferenceType,
  ScheduleEntryType,
  ScheduleRequirementType}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Indexes}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.{set, unset}
import zio.{RIO, Task, ZIO}

import scala.reflect.ClassTag

trait CommonLiveOperations extends CommonFields with FightFieldsAndFilters {

  private final val competitionStateCollectionName   = "competition_state"
  private final val competitorsCollectionName        = "competitor"
  private final val fightsCollectionName             = "fight"
  private final val managedCompetitionCollectionName = "managed_competition"
  private final val academyCollectionName            = "academy"

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

  private val caseClassRegistry = fromRegistries(
    fromProviders(classOf[FightStatus]),
    fromProviders(classOf[CompetitorRegistrationStatus]),
    fromProviders(classOf[CompetitionStatus]),
    fromProviders(classOf[DistributionType]),
    fromProviders(classOf[StageRoundType]),
    fromProviders(classOf[GroupSortDirection]),
    fromProviders(classOf[LogicalOperator]),
    fromProviders(classOf[GroupSortSpecifier]),
    fromProviders(classOf[SelectorClassifier]),
    fromProviders(classOf[OperatorType]),
    fromProviders(classOf[BracketType]),
    fromProviders(classOf[StageType]),
    fromProviders(classOf[StageStatus]),
    fromProviders(classOf[CategoryRestrictionType]),
    fromProviders(classOf[FightReferenceType]),
    fromProviders(classOf[ScheduleEntryType]),
    fromProviders(classOf[ScheduleRequirementType]),
    fromProviders(classOf[FullAcademyInfo]),
    fromProviders(classOf[CompetitionState]),
    fromProviders(classOf[Period]),
    fromProviders(classOf[RegistrationPeriod]),
    fromProviders(classOf[RegistrationGroup]),
    fromProviders(classOf[RegistrationFee]),
    fromProviders(classOf[StageDescriptor]),
    fromProviders(classOf[StageResultDescriptor]),
    fromProviders(classOf[StageInputDescriptor]),
    fromProviders(classOf[CompetitorSelector]),
    fromProviders(classOf[GroupDescriptor]),
    fromProviders(classOf[FightResultOption]),
    fromProviders(classOf[CompetitorStageResult]),
    fromProviders(classOf[AdditionalGroupSortingDescriptor]),
    fromProviders(classOf[CompetitionInfoTemplate]),
    fromProviders(classOf[ScheduleEntry]),
    fromProviders(classOf[MatIdAndSomeId]),
    fromProviders(classOf[Restriction]),
    fromProviders(classOf[ScheduleRequirement]),
    fromProviders(classOf[CompetitionProperties]),
    fromProviders(classOf[RegistrationInfo]),
    fromProviders(classOf[Competitor]),
    fromProviders(classOf[Fight]),
    fromProviders(classOf[BracketsInfo]),
    fromProviders(classOf[FightResult]),
    fromProviders(classOf[CompScore]),
    fromProviders(classOf[Score]),
    fromProviders(classOf[PointGroup]),
    fromProviders(classOf[Academy]),
    fromProviders(classOf[Mat]),
    fromProviders(classOf[Category]),
    fromProviders(classOf[ManagedCompetition]),
    DEFAULT_CODEC_REGISTRY
  )

  def mongoClient: MongoClient
  def dbName: String

  private def createCollection[T: ClassTag](name: String, id: String) = {
    (for {
      collection <- ZIO.effect(database.getCollection[T](name))
      _ <- ZIO
        .fromFuture(_ => collection.createIndex(Indexes.ascending(id), new IndexOptions().unique(true)).toFuture())
    } yield collection).memoize.flatten
  }

  val database: MongoDatabase = mongoClient.getDatabase(dbName).withCodecRegistry(fromRegistries(caseClassRegistry))
  val competitionStateCollection: Task[MongoCollection[CompetitionState]] =
    createCollection(competitionStateCollectionName, idField)
  val competitorCollection: Task[MongoCollection[Competitor]] = createCollection(competitorsCollectionName, idField)
  val fightCollection: Task[MongoCollection[Fight]] = for {
    coll <- createCollection[Fight](fightsCollectionName, idField)
    _    <- ZIO.fromFuture(_ => coll.createIndex(fightsCollectionIndex).toFuture())
  } yield coll
  val managedCompetitionCollection: Task[MongoCollection[ManagedCompetition]] =
    createCollection(managedCompetitionCollectionName, idField)
  val academyCollection: Task[MongoCollection[FullAcademyInfo]] = createCollection(academyCollectionName, idField)

  protected def setOption[T](name: String, opt: Option[T]): Bson = opt.map(v => set(name, v)).getOrElse(unset(name))
  protected def deleteById[T](collectionTask: Task[MongoCollection[T]])(id: String): LIO[Unit] = {
    for {
      collection <- collectionTask
      delete = collection.deleteMany(equal(idField, id))
      _ <- RIO.fromFuture(_ => delete.toFuture())
    } yield ()
  }
  protected def insertElement[T](collectionTask: Task[MongoCollection[T]])(id: String, element: T): LIO[Unit] = {
    for {
      collection <- collectionTask
      insert = collection.replaceOne(Filters.eq(idField, id), element, new ReplaceOptions().upsert(true))
      _ <- RIO.fromFuture(_ => insert.toFuture())
    } yield ()
  }
}
