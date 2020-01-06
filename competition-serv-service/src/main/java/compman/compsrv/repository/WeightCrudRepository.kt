package compman.compsrv.repository


import compman.compsrv.jpa.competition.Weight
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface WeightCrudRepository : JpaRepository<Weight, String>