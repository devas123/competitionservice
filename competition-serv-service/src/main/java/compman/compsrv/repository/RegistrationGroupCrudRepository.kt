package compman.compsrv.repository

import compman.compsrv.jpa.competition.RegistrationGroup
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface RegistrationGroupCrudRepository : CrudRepository<RegistrationGroup, Long>