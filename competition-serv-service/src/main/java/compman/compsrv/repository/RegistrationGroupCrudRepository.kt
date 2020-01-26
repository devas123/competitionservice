package compman.compsrv.repository

import compman.compsrv.jpa.competition.RegistrationGroup
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
@Transactional(propagation = Propagation.SUPPORTS)
interface RegistrationGroupCrudRepository : CrudRepository<RegistrationGroup, String> {
    @Query("SELECT * FROM registration_group g WHERE g.default_group = true AND g.registration_info_id = ?1 AND g.id != ?2", nativeQuery = true)
    fun findDefaultGroupByRegistrationInfoIdAndIdNotEqual(registrationGroupId: String, id: String): Optional<RegistrationGroup>

    fun deleteAllByRegistrationInfoId(registrationInfoId: String)
}