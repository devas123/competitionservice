package compman.compsrv.query.model

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.schedule.{MatIdAndSomeId, PeriodDTO}

import java.util.Date

package object extensions {

  implicit class PeriodOps(p: PeriodDTO) {
    def enrichWithFightsByScheduleEntries(fightsByScheduleEntries: List[FightByScheduleEntry]): PeriodDTO = {
      val map = fightsByScheduleEntries.filter(e => e.matId.isDefined && e.periodId == p.getId)
        .groupMap(_.scheduleEntryId)(e =>
          new MatIdAndSomeId(e.matId.orNull, e.startTime.map(_.toInstant).orNull, e.fightId)
        )
      p.setScheduleEntries(
        Option(p.getScheduleEntries).map(_.map(se =>
          se.setFightIds(map.get(se.getId).map(_.toArray).getOrElse(Array.empty))
        )).getOrElse(Array.empty)
      )
    }
  }

  implicit class CompetitionPropertiesOps(c: CompetitionProperties) {
    def applyProperties(props: CompetitionPropertiesDTO): CompetitionProperties = {
      for { pr <- Option(props) } yield c.copy(
        timeZone = pr.getTimeZone,
        schedulePublished = pr.getSchedulePublished,
        competitionName = pr.getCompetitionName,
        bracketsPublished = pr.getBracketsPublished,
        startDate = Date.from(pr.getStartDate),
        endDate = Option(pr.getEndDate).map(Date.from)
      )
    }.getOrElse(c)

  }
}
