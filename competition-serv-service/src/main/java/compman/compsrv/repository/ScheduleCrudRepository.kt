package compman.compsrv.repository

import compman.compsrv.model.schedule.Schedule
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Component

@Component
interface ScheduleCrudRepository : MongoRepository<Schedule, String>