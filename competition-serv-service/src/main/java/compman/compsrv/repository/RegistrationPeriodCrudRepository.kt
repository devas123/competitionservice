package compman.compsrv.repository

import compman.compsrv.jpa.competition.RegistrationPeriod
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RegistrationPeriodCrudRepository : CrudRepository<RegistrationPeriod, Long>