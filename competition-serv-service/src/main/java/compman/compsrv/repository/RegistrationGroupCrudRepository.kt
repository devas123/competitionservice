package compman.compsrv.repository

import compman.compsrv.jpa.competition.RegistrationGroup
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(propagation = Propagation.SUPPORTS)
interface RegistrationGroupCrudRepository : CrudRepository<RegistrationGroup, String>