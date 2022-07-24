package compman.compsrv.logic.actor.kafka.persistence

trait EventSourcingOperations[Command, Event, State, Error] {
  def processCommand(command: Command, state: State): Either[Error, Seq[Event]]
  def applyEvent(event: Event, state: State): State
  def optionallySaveStateSnapshot(state: State): Unit
  def processError(command: Command, error: Error, state: State): State
}
