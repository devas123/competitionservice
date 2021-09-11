package compman.compsrv.query.service.repository

import zio.{Has, URIO, ZIO}

object ManagedCompetitions {
  type Service = Has[ManagedCompetitionService]

  trait ManagedCompetitionService {
    def getManagedCompetitions: URIO[Service, List[ManagedCompetition]]
    def addManagedCompetition(competition: ManagedCompetition): URIO[Service, Unit]
    def deleteManagedCompetition(id: String): URIO[Service, Unit]
  }

  def getManagedCompetitions: URIO[Service, List[ManagedCompetition]] = ZIO
    .accessM(_.get[ManagedCompetitionService].getManagedCompetitions)
  def addManagedCompetition(competition: ManagedCompetition): URIO[Service, Unit] = ZIO
    .accessM(_.get[ManagedCompetitionService].addManagedCompetition(competition))
  def deleteManagedCompetition(id: String): URIO[Service, Unit] = ZIO
    .accessM(_.get[ManagedCompetitionService].deleteManagedCompetition(id))

  case class ManagedCompetition(competitionId: String, eventTopic: String)
}
