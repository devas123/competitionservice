package compman.compsrv.query.service.repository

import zio.{Has, URIO}

object ManagedCompetitions {
  type Service = Has[ManagedCompetitionService]

  trait ManagedCompetitionService {
    def getManagedCompetitions: URIO[Service, List[ManagedCompetition]]
    def addManagedCompetition(competition: ManagedCompetition): URIO[Service, Unit]
    def deleteManagedCompetition(id: String): URIO[Service, Unit]
  }

  case class ManagedCompetition(competitionId: String, eventTopic: String)
}
