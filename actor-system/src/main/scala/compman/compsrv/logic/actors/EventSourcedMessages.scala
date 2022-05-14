package compman.compsrv.logic.actors

object EventSourcedMessages {
  sealed trait EventSourcingCommand[+Ev] extends Product

  object EventSourcingCommand {
    case class Persist[+Ev](event: Seq[Ev]) extends EventSourcingCommand[Ev]

    case object Ignore extends EventSourcingCommand[Nothing]

    def persist[Ev](event: Seq[Ev]): Persist[Ev] = Persist(event)

    def ignore: Ignore.type = Ignore
  }
}
