package compman.compsrv.query.model

import compman.compsrv.model.dto.competition.CompetitionStatus

import java.time.Instant

case class CompetitionProperties(
                           id: String, creatorId: String,
                           staffIds: Array[String],
                           competitionName: String,
                           emailTemplate: String,
                           startDate: Instant,
                           schedulePublished: Boolean,
                           bracketsPublished: Boolean,
                           endDate: Instant,
                           timeZone: String,
                           creationTimestamp: Instant,
                           status: CompetitionStatus)