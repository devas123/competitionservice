package compman.compsrv.repository

import compman.compsrv.jpa.competition.RegistrationInfo
import compman.compsrv.jpa.schedule.Schedule
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RegistrationInfoCrudRepository : CrudRepository<RegistrationInfo, String>