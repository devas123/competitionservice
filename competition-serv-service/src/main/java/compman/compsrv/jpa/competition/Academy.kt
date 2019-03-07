package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import java.util.*
import javax.persistence.ElementCollection
import javax.persistence.Entity


@Entity
class Academy(
        id: String = UUID.randomUUID().toString(),
        var name: String,
        @ElementCollection
        var coaches: List<String>?,
        var created: Long?) : AbstractJpaPersistable<String>(id) {
    constructor(name: String, coaches: List<String>) : this(name = name, coaches = coaches, created = System.currentTimeMillis())
}