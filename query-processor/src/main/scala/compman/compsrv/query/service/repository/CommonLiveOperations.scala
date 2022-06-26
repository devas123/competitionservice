package compman.compsrv.query.service.repository

import com.mongodb.client.model.{IndexOptions, ReplaceOptions}
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import compman.compsrv.query.model.academy.FullAcademyInfo
import compservice.model.protobuf.model.{BracketType, CategoryRestrictionType, CompetitionStatus, CompetitorRegistrationStatus, DistributionType, FightReferenceType, FightStatus, GroupSortDirection, GroupSortSpecifier, LogicalOperator, OperatorType, ScheduleEntryType, ScheduleRequirementType, SelectorClassifier, StageRoundType, StageStatus, StageType}
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.mongodb.scala.{FindObservable, MongoClient, MongoCollection, MongoDatabase, Observable}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Indexes}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Updates.{set, unset}
import scalapb.{GeneratedEnum, GeneratedEnumCompanion}
import zio.{RIO, Task, ZIO}

import scala.concurrent.Future
import scala.reflect.ClassTag

trait CommonLiveOperations extends CommonFields with FightFieldsAndFilters {

  private final val competitionStateCollectionName   = "competition_state"
  private final val competitorsCollectionName        = "competitor"
  private final val fightsCollectionName             = "fight"
  private final val managedCompetitionCollectionName = "managed_competition"
  private final val academyCollectionName            = "academy"
  private final val eventOffsetCollectionName        = "event_offset"

  import org.bson.codecs.configuration.CodecRegistries.{fromCodecs, fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

  val competitionStatusCodec: Seq[Codec[CompetitionStatus]] =
    cd[CompetitionStatus](classOf[CompetitionStatus], CompetitionStatus)
  val distributionTypeCodec: Seq[Codec[DistributionType]] =
    cd[DistributionType](classOf[DistributionType], DistributionType)
  val stageRoundTypeCodec: Seq[Codec[StageRoundType]] = cd[StageRoundType](classOf[StageRoundType], StageRoundType)
  val groupSortDirectionCodec: Seq[Codec[GroupSortDirection]] =
    cd[GroupSortDirection](classOf[GroupSortDirection], GroupSortDirection)
  val logicalOperatorCodec: Seq[Codec[LogicalOperator]] = cd[LogicalOperator](classOf[LogicalOperator], LogicalOperator)
  val groupSortSpecifierCodec: Seq[Codec[GroupSortSpecifier]] =
    cd[GroupSortSpecifier](classOf[GroupSortSpecifier], GroupSortSpecifier)
  val selectorClassifierCodec: Seq[Codec[SelectorClassifier]] =
    cd[SelectorClassifier](classOf[SelectorClassifier], SelectorClassifier)
  val operatorTypeCodec: Seq[Codec[OperatorType]] = cd[OperatorType](classOf[OperatorType], OperatorType)
  val bracketTypeCodec: Seq[Codec[BracketType]]   = cd[BracketType](classOf[BracketType], BracketType)
  val stageTypeCodec: Seq[Codec[StageType]]       = cd[StageType](classOf[StageType], StageType)
  val stageStatusCodec: Seq[Codec[StageStatus]]   = cd[StageStatus](classOf[StageStatus], StageStatus)
  val categoryRestrictionTypeCodec: Seq[Codec[CategoryRestrictionType]] =
    cd[CategoryRestrictionType](classOf[CategoryRestrictionType], CategoryRestrictionType)
  val fightReferenceTypeCodec: Seq[Codec[FightReferenceType]] =
    cd[FightReferenceType](classOf[FightReferenceType], FightReferenceType)
  val scheduleEntryTypeCodec: Seq[Codec[ScheduleEntryType]] =
    cd[ScheduleEntryType](classOf[ScheduleEntryType], ScheduleEntryType)
  val scheduleRequirementTypeCodec: Seq[Codec[ScheduleRequirementType]] =
    cd[ScheduleRequirementType](classOf[ScheduleRequirementType], ScheduleRequirementType)
  val competitorRegistrationStatusCodec: Seq[Codec[CompetitorRegistrationStatus]] =
    cd[CompetitorRegistrationStatus](classOf[CompetitorRegistrationStatus], CompetitorRegistrationStatus)
  val fightStatusCodec: Seq[Codec[FightStatus]] = cd[FightStatus](classOf[FightStatus], FightStatus)

  private val caseClassRegistry = fromRegistries(
    fromCodecs(competitionStatusCodec: _*),
    fromCodecs(distributionTypeCodec: _*),
    fromCodecs(stageRoundTypeCodec: _*),
    fromCodecs(groupSortDirectionCodec: _*),
    fromCodecs(logicalOperatorCodec: _*),
    fromCodecs(groupSortSpecifierCodec: _*),
    fromCodecs(selectorClassifierCodec: _*),
    fromCodecs(operatorTypeCodec: _*),
    fromCodecs(bracketTypeCodec: _*),
    fromCodecs(stageTypeCodec: _*),
    fromCodecs(stageStatusCodec: _*),
    fromCodecs(categoryRestrictionTypeCodec: _*),
    fromCodecs(fightReferenceTypeCodec: _*),
    fromCodecs(scheduleEntryTypeCodec: _*),
    fromCodecs(scheduleRequirementTypeCodec: _*),
    fromCodecs(competitorRegistrationStatusCodec: _*),
    fromCodecs(fightStatusCodec: _*),
    fromProviders(classOf[EventOffset]),
    fromProviders(classOf[FullAcademyInfo]),
    fromProviders(classOf[CompetitionState]),
    fromProviders(classOf[Period]),
    fromProviders(classOf[RegistrationPeriod]),
    fromProviders(classOf[FightReference]),
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
  val eventOffsetCollection: Task[MongoCollection[EventOffset]] = createCollection(eventOffsetCollectionName, "topic")

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

  def cd[T <: GeneratedEnum: ClassTag](clazz: Class[T], companion: GeneratedEnumCompanion[T]): Seq[Codec[T]] = {
    companion.values.map(v => new EnumCodec(v.getClass.asInstanceOf[Class[T]], companion).asInstanceOf[Codec[T]]) :+
      new EnumCodec[T](clazz, companion)
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

  protected def selectWithPagination[T](
    select: FindObservable[T],
    pagination: Option[Pagination],
    total: Future[Long]
  ): ZIO[Any, Throwable, (List[T], Pagination)] = {
    for {
      res <- runQuery(select)
      tr  <- RIO.fromFuture(_ => total)
    } yield (res, pagination.map(_.copy(totalResults = tr.toInt)).getOrElse(Pagination(0, res.size, tr.toInt)))
  }

  protected def selectOne[T](select: Observable[T]): ZIO[Any, Throwable, Option[T]] = {
    for { res <- runQuery(select) } yield res.headOption
  }

  protected def runQuery[T](select: Observable[T]): ZIO[Any, Throwable, List[T]] = {
    for { res <- RIO.fromFuture(_ => select.toFuture()) } yield res.toList
  }


  protected def getByIdAndCompetitionId[T: ClassTag](collection: MongoCollection[T])(competitionId: String, id: String): LIO[Option[T]] = {
    for {
      statement <- ZIO.effect(collection
        .find(and(equal(competitionIdField, competitionId), equal(idField, id))))
      res <- selectOne(statement)
    } yield res
  }

}
