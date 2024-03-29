package compman.compsrv.query.model

import compman.compsrv.model.extensions.InstantOps
import compman.compsrv.query.model.mapping.DtoMapping.toDate
import compservice.model.protobuf.model
import compservice.model.protobuf.model.StartTimeInfo

package object extensions {

  val getStartTimeInfoUniqueID: StartTimeInfo => String = (fs: StartTimeInfo) => fs.matId + fs.someId

  final implicit class PeriodOps(private val p: model.Period) extends AnyVal {
    def enrichWithFightsByScheduleEntries(fightsByScheduleEntries: List[FightByScheduleEntry]): model.Period = {
      val map = fightsByScheduleEntries.filter(e => e.matId.isDefined && e.periodId == p.id)
        .groupMap(_.scheduleEntryId)(e =>
          model.StartTimeInfo(e.matId.orNull, e.startTime.map(_.toInstant.asTimestamp), e.fightId)
        )
      p.withScheduleEntries(
        Option(p.scheduleEntries).map(_.map(se =>
          se.withFightScheduleInfo(
            (se.fightScheduleInfo ++ map.getOrElse(se.id, Seq.empty)).distinctBy(getStartTimeInfoUniqueID)
          )
        )).getOrElse(Seq.empty)
      )
    }
  }

  final implicit class CompetitionPropertiesOps(private val c: CompetitionProperties) extends AnyVal {
    def applyProperties(props: model.CompetitionProperties): CompetitionProperties = {
      for { pr <- Option(props) } yield c.copy(
        timeZone = pr.timeZone,
        schedulePublished = pr.schedulePublished,
        competitionName = pr.competitionName,
        bracketsPublished = pr.bracketsPublished,
        startDate = pr.startDate.map(toDate).get,
        endDate = pr.endDate.map(toDate),
        status = pr.status
      )
    }.getOrElse(c)

  }
}
