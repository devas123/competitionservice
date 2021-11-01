package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{
  CategoryRestrictionType,
  CompetitionStatus,
  CompetitorRegistrationStatus,
  FightStatus
}
import compman.compsrv.model.dto.schedule.{ScheduleEntryType, ScheduleRequirementType}
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import org.bson.codecs.pojo.{ClassModel, PojoCodecProvider}
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

trait CommonLiveOperations {
  private final val competitionStateCollectionName = "competition_state"

  private final val competitorsCollectionName = "competitor"

  private final val fightsCollectionName = "fight"

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

  val enumsProvider: PojoCodecProvider = PojoCodecProvider.builder().register(
    ClassModel.builder(classOf[CompetitionStatus]).build(),
    ClassModel.builder(classOf[DistributionType]).build(),
    ClassModel.builder(classOf[StageRoundType]).build(),
    ClassModel.builder(classOf[GroupSortDirection]).build(),
    ClassModel.builder(classOf[LogicalOperator]).build(),
    ClassModel.builder(classOf[GroupSortSpecifier]).build(),
    ClassModel.builder(classOf[SelectorClassifier]).build(),
    ClassModel.builder(classOf[OperatorType]).build(),
    ClassModel.builder(classOf[BracketType]).build(),
    ClassModel.builder(classOf[StageType]).build(),
    ClassModel.builder(classOf[StageStatus]).build(),
    ClassModel.builder(classOf[CategoryRestrictionType]).build(),
    ClassModel.builder(classOf[FightReferenceType]).build(),
    ClassModel.builder(classOf[ScheduleEntryType]).build(),
    ClassModel.builder(classOf[ScheduleRequirementType]).build(),
    ClassModel.builder(classOf[CompetitorRegistrationStatus]).build(),
    ClassModel.builder(classOf[FightStatus]).build()
  ).build()

  private val codecRegistry = fromRegistries(
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
    fromProviders(enumsProvider),
    DEFAULT_CODEC_REGISTRY
  )

  def mongoClient: MongoClient
  def dbName: String
  def idField: String

  def database: MongoDatabase = mongoClient.getDatabase(dbName).withCodecRegistry(codecRegistry)
  def competitionStateCollection: MongoCollection[CompetitionState] = database
    .getCollection(competitionStateCollectionName)
  def competitorCollection: MongoCollection[Competitor] = database.getCollection(competitorsCollectionName)
  def fightCollection: MongoCollection[Fight]           = database.getCollection(fightsCollectionName)
}
