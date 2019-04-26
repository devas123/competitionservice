package compman.compsrv.repository

import compman.compsrv.jpa.competition.RegistrationPeriod
import compman.compsrv.jpa.schedule.DashboardPeriod
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(propagation = Propagation.SUPPORTS)
interface DashboardPeriodCrudRepository : CrudRepository<DashboardPeriod, String>