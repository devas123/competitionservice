package compman.compsrv.query.service.repository

import com.mongodb.client.model.{IndexOptions, ReplaceOptions}
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import compman.compsrv.query.model.academy.FullAcademyInfo
import compservice.model.protobuf.model.{
  BracketType,
  CategoryRestrictionType,
  CompetitionStatus,
  CompetitorRegistrationStatus,
  DistributionType,
  FightReferenceType,
  FightStatus,
  GroupSortDirection,
  GroupSortSpecifier,
  LogicalOperator,
  OperatorType,
  ScheduleEntryType,
  ScheduleRequirementType,
  SelectorClassifier,
  StageRoundType,
  StageStatus,
  StageType
}
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Indexes}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.{set, unset}
import scalapb.{GeneratedEnum, GeneratedEnumCompanion}
import zio.{RIO, Task, ZIO}

import scala.reflect.ClassTag

trait CommonLiveOperations extends CommonFields with FightFieldsAndFilters {

  private final val competitionStateCollectionName   = "competition_state"
  private final val competitorsCollectionName        = "competitor"
  private final val fightsCollectionName             = "fight"
  private final val managedCompetitionCollectionName = "managed_competition"
  private final val academyCollectionName            = "academy"

  import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

  val competitionStatusCodec  = new EnumCodec[CompetitionStatus](classOf[CompetitionStatus], CompetitionStatus)
  val distributionTypeCodec   = new EnumCodec[DistributionType](classOf[DistributionType], DistributionType)
  val stageRoundTypeCodec     = new EnumCodec[StageRoundType](classOf[StageRoundType], StageRoundType)
  val groupSortDirectionCodec = new EnumCodec[GroupSortDirection](classOf[GroupSortDirection], GroupSortDirection)
  val logicalOperatorCodec    = new EnumCodec[LogicalOperator](classOf[LogicalOperator], LogicalOperator)
  val groupSortSpecifierCodec = new EnumCodec[GroupSortSpecifier](classOf[GroupSortSpecifier], GroupSortSpecifier)
  val selectorClassifierCodec = new EnumCodec[SelectorClassifier](classOf[SelectorClassifier], SelectorClassifier)
  val operatorTypeCodec       = new EnumCodec[OperatorType](classOf[OperatorType], OperatorType)
  val bracketTypeCodec        = new EnumCodec[BracketType](classOf[BracketType], BracketType)
  val stageTypeCodec          = new EnumCodec[StageType](classOf[StageType], StageType)
  val stageStatusCodec        = new EnumCodec[StageStatus](classOf[StageStatus], StageStatus)
  val categoryRestrictionTypeCodec =
    new EnumCodec[CategoryRestrictionType](classOf[CategoryRestrictionType], CategoryRestrictionType)
  val fightReferenceTypeCodec = new EnumCodec[FightReferenceType](classOf[FightReferenceType], FightReferenceType)
  val scheduleEntryTypeCodec  = new EnumCodec[ScheduleEntryType](classOf[ScheduleEntryType], ScheduleEntryType)
  val scheduleRequirementTypeCodec =
    new EnumCodec[ScheduleRequirementType](classOf[ScheduleRequirementType], ScheduleRequirementType)
  val competitorRegistrationStatusCodec =
    new EnumCodec[CompetitorRegistrationStatus](classOf[CompetitorRegistrationStatus], CompetitorRegistrationStatus)
  val fightStatusCodec = new EnumCodec[FightStatus](classOf[FightStatus], FightStatus)

  private val caseClassRegistry = fromRegistries(
    fromCodecs(
      competitionStatusCodec,
      distributionTypeCodec,
      stageRoundTypeCodec,
      groupSortDirectionCodec,
      logicalOperatorCodec,
      groupSortSpecifierCodec,
      selectorClassifierCodec,
      operatorTypeCodec,
      bracketTypeCodec,
      stageTypeCodec,
      stageStatusCodec,
      categoryRestrictionTypeCodec,
      fightReferenceTypeCodec,
      scheduleEntryTypeCodec,
      scheduleRequirementTypeCodec,
      competitorRegistrationStatusCodec,
      fightStatusCodec
    ),
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

  class EnumCodec[T <: GeneratedEnum: ClassTag](val clazz: Class[T], companion: GeneratedEnumCompanion[T])
      extends Codec[T] {
    override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit = {
      writer.writeString(value.value.toString)
    }
    override def getEncoderClass: Class[T] = clazz
    override def decode(reader: BsonReader, decoderContext: DecoderContext): T = {
      companion.fromValue(reader.readString().toInt)
    }
  }
}
