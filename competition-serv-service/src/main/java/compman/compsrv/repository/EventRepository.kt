package compman.compsrv.repository

import compman.compsrv.jpa.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.MANDATORY)
interface EventRepository : JpaRepository<Event, String> {
    fun existsByCorrelationId(correlationId: String): Boolean
}