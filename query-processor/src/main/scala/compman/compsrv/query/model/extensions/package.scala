package compman.compsrv.query.model

import java.time.Instant

package object extensions {

  private def parseDate(date: Any, default: Option[Instant] = None) =
    if (date != null) { Some(Instant.ofEpochMilli(date.toString.toLong)) }
    else { default }

  implicit class CompetitionPropertiesOps(c: CompetitionProperties) {
    def applyProperties(props: Map[String, String]): CompetitionProperties = {
      for {
        pr <- Option(props)
        bracketsPublished <- pr.get("bracketsPublished").map(_.toBoolean)
          .orElse(Option(c.bracketsPublished).map(_.booleanValue()))
        startDate       <- parseDate(pr("startDate"), Option(c.startDate))
        endDate         <- parseDate(pr("endDate"), Option(c.endDate))
        competitionName <- pr.get("competitionName").orElse(Option(c.competitionName))
        schedulePublished <- pr.get("schedulePublished").map(_.toBoolean)
          .orElse(Option(c.schedulePublished).map(_.booleanValue()))
        timeZone <- pr.get("timeZone").orElse(Option(c.timeZone))
      } yield c.copy(
        timeZone = timeZone,
        schedulePublished = schedulePublished,
        competitionName = competitionName,
        bracketsPublished = bracketsPublished,
        startDate = startDate,
        endDate = endDate
      )
    }.getOrElse(c)

  }
}
