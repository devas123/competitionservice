package compman.compsrv.query.service.repository

import compman.compsrv.query.model.{CompetitionState, Competitor, Fight}
import org.mongodb.scala.{MongoClient, MongoCollection}

trait CommonLiveOperations {
  private final val competitionStateCollectionName = "competition_state"

  private final val competitorsCollectionName = "competitor"

  private final val fightsCollectionName = "fight"

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

  private val codecRegistry = fromRegistries(
    fromProviders(classOf[CompetitionState]),
    fromProviders(classOf[Competitor]),
    fromProviders(classOf[Fight]),
    DEFAULT_CODEC_REGISTRY
  )

  def mongoClient: MongoClient
  def dbName: String
  def idField: String

  def database = mongoClient.getDatabase(dbName).withCodecRegistry(codecRegistry)
  def competitionStateCollection: MongoCollection[CompetitionState] = database
    .getCollection(competitionStateCollectionName)
  def competitorCollection: MongoCollection[Competitor] = database.getCollection(competitorsCollectionName)
  def fightCollection: MongoCollection[Fight]           = database.getCollection(fightsCollectionName)
}