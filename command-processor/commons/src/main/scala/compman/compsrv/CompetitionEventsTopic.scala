package compman.compsrv

object CompetitionEventsTopic {
  def apply(eventPrefix: String): String => String = (competitinId: String) => s"$competitinId-$eventPrefix"
}
