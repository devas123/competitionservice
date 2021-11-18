package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CategoryRestrictionType, CompetitionStatus, CompetitorRegistrationStatus, FightStatus}
import compman.compsrv.model.dto.schedule.{ScheduleEntryType, ScheduleRequirementType}
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecRegistries.fromCodecs
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

trait CommonLiveOperations {
  private final val competitionStateCollectionName = "competition_state"

  private final val competitorsCollectionName = "competitor"

  private final val fightsCollectionName = "fight"
  private final val managedCompetitionCollectionName = "managed_competition"

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY


  class EnumCodec[T <: Enum[T]](val clazz: Class[T]) extends Codec[T] {
    override def encode(writer: BsonWriter, value: T, encoderContext: EncoderContext): Unit = {
      writer.writeString(value.name)
    }
    override def getEncoderClass: Class[T] = clazz
    override def decode(reader: BsonReader, decoderContext: DecoderContext): T = Enum.valueOf(clazz, reader.readString)
  }

  val competitionStatusCodec = new EnumCodec[CompetitionStatus](classOf[CompetitionStatus])
  val distributionTypeCodec = new EnumCodec[DistributionType](classOf[DistributionType])
  val stageRoundTypeCodec = new EnumCodec[StageRoundType](classOf[StageRoundType])
  val groupSortDirectionCodec = new EnumCodec[GroupSortDirection](classOf[GroupSortDirection])
  val logicalOperatorCodec = new EnumCodec[LogicalOperator](classOf[LogicalOperator])
  val groupSortSpecifierCodec = new EnumCodec[GroupSortSpecifier](classOf[GroupSortSpecifier])
  val selectorClassifierCodec = new EnumCodec[SelectorClassifier](classOf[SelectorClassifier])
  val operatorTypeCodec = new EnumCodec[OperatorType](classOf[OperatorType])
  val bracketTypeCodec = new EnumCodec[BracketType](classOf[BracketType])
  val stageTypeCodec = new EnumCodec[StageType](classOf[StageType])
  val stageStatusCodec = new EnumCodec[StageStatus](classOf[StageStatus])
  val categoryRestrictionTypeCodec = new EnumCodec[CategoryRestrictionType](classOf[CategoryRestrictionType])
  val fightReferenceTypeCodec = new EnumCodec[FightReferenceType](classOf[FightReferenceType])
  val scheduleEntryTypeCodec = new EnumCodec[ScheduleEntryType](classOf[ScheduleEntryType])
  val scheduleRequirementTypeCodec = new EnumCodec[ScheduleRequirementType](classOf[ScheduleRequirementType])
  val competitorRegistrationStatusCodec = new EnumCodec[CompetitorRegistrationStatus](classOf[CompetitorRegistrationStatus])
  val fightStatusCodec = new EnumCodec[FightStatus](classOf[FightStatus])


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
    fromProviders(classOf[CompetitionState]),
    fromProviders(classOf[Period]),
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
    fromProviders(classOf[Category]),
    fromProviders(classOf[ManagedCompetition]),
    DEFAULT_CODEC_REGISTRY
  )


  def mongoClient: MongoClient
  def dbName: String
  def idField: String

  lazy val database: MongoDatabase = mongoClient.getDatabase(dbName).withCodecRegistry(fromRegistries(caseClassRegistry))
  lazy val competitionStateCollection: MongoCollection[CompetitionState] = database
    .getCollection(competitionStateCollectionName)
  lazy val competitorCollection: MongoCollection[Competitor] = database.getCollection(competitorsCollectionName)
  lazy val fightCollection: MongoCollection[Fight]           = database.getCollection(fightsCollectionName)
  lazy val managedCompetitionCollection: MongoCollection[ManagedCompetition] = database.getCollection(managedCompetitionCollectionName)
}