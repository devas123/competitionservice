package compman.compsrv.repository

import compman.compsrv.jpa.competition.RegistrationInfo
import compman.compsrv.jpa.schedule.Schedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(propagation = Propagation.SUPPORTS)
interface RegistrationInfoCrudRepository : JpaRepository<RegistrationInfo, String>