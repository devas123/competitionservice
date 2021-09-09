package compman.compsrv.logic.actors

case class ActorConfig(competitionId: String, eventTopic: String, actorIdleTimeoutMillis: Option[Long])

object ActorConfig {
  def defaultEventsTopic(id: String) = s"events_$id"
}
