package compman.compsrv.repository

import compman.compsrv.model.schedule.Schedule
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ScheduleCrudRepository : CrudRepository<Schedule, String>