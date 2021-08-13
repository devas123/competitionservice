package compman.compsrv.logic.actors

import compman.compsrv.model.commands.CommandDTO

object Messages {
  private[actors] sealed trait Message

  final case class ProcessCommand(fa: CommandDTO) extends Message

  private[actors] object Stop extends Message

  private[actors] sealed trait Command[+Ev]

  private[actors] object Command {
    case class Persist[+Ev](event: Seq[Ev]) extends Command[Ev]

    case object Ignore extends Command[Nothing]

    def persist[Ev](event: Seq[Ev]): Persist[Ev] = Persist(event)

    def ignore: Ignore.type = Ignore
  }
}