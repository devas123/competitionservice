package compman.compsrv.repository


import compman.compsrv.jpa.competition.FightDescription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.MANDATORY)
interface FightCrudRepository : JpaRepository<FightDescription, String>