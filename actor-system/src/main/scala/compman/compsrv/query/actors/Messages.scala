package compman.compsrv.query.actors

object Messages {
  sealed trait Command[+Ev] extends Product

  object Command {
    case class Persist[+Ev](event: Seq[Ev]) extends Command[Ev]

    case object Ignore extends Command[Nothing]

    def persist[Ev](event: Seq[Ev]): Persist[Ev] = Persist(event)

    def ignore: Ignore.type = Ignore
  }
}
