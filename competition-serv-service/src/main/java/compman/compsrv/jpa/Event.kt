package compman.compsrv.jpa

import compman.compsrv.model.events.EventType
import javax.persistence.Basic
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Lob

@Entity
class Event(
        id: String?,
        var type: EventType?,
        var competitionId: String?,
        var correlationId: String?,
        var categoryId: String?,
        var matId: String?,
        @Lob @Basic(fetch = FetchType.LAZY)
        var payload: String?) : AbstractJpaPersistable<String>(id)