package compman.compsrv.logic.schedule

import compservice.model.protobuf.model.{FightDescription, FightDigraph, FightReference, FightReferenceList}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object FightGraph {

  def createMutableMapOfStringToListOfFightReference(
    fights: Iterable[FightDescription]
  ): mutable.Map[String, ArrayBuffer[FightReference]] = {
    val result = mutable.HashMap.empty[String, ArrayBuffer[FightReference]]
    for (fight <- fights) { result.put(fight.id, mutable.ArrayBuffer.empty) }
    result
  }

  def fightDiGraph(
    incomingConnections: mutable.Map[String, ArrayBuffer[FightReference]],
    outgoingConnections: mutable.Map[String, ArrayBuffer[FightReference]]
  ): FightDigraph = {
    FightDigraph()
      .withIncomingConnections(incomingConnections.view.mapValues(v => FightReferenceList(v.distinct.toSeq)).toMap)
      .withOutgoingConnections(outgoingConnections.view.mapValues(v => FightReferenceList(v.distinct.toSeq)).toMap)
  }

  def createFightsGraph(fights: Iterable[FightDescription]): FightDigraph = {
    val incomingConnections = createMutableMapOfStringToListOfFightReference(fights)
    val outgoingConnections = createMutableMapOfStringToListOfFightReference(fights)
    for (
      fight <- fights;
      score <- fight.scores
    ) {
      val parentId = score.parentFightId
      if (parentId.nonEmpty && parentId.exists(_.nonEmpty)) {
        parentId.foreach { id =>
          val reference = FightReference().withFightId(id).withReferenceType(score.getParentReferenceType)
          incomingConnections(fight.id).append(reference)
          outgoingConnections.getOrElseUpdate(id, mutable.ArrayBuffer.empty).append(reference.withFightId(fight.id))
        }
      }
    }
    fightDiGraph(incomingConnections, outgoingConnections)
  }

}
