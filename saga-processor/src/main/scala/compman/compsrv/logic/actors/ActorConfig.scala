package compman.compsrv.logic.actors

trait ActorConfig {
  def eventTopic: String = ActorConfig.defaultEventsTopic(id)
  def id: String
}

object ActorConfig {
  def defaultEventsTopic(id: String) = s"$id-events"
  def apply(competitionId: String): ActorConfig = new ActorConfig {
    override def id: String = competitionId
  }
}
