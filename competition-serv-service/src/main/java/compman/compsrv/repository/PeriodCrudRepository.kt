package compman.compsrv.repository

import compman.compsrv.jpa.schedule.Period
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PeriodCrudRepository : CrudRepository<Period, String>