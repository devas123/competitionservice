package compman.compsrv.jpa

import java.io.Serializable
import javax.persistence.Id
import javax.persistence.MappedSuperclass
import javax.persistence.Version

@MappedSuperclass
abstract class AbstractJpaPersistable<T : Serializable>(@Id var id: T? = null) {

    override fun equals(other: Any?): Boolean {
        other ?: return false

        if (this === other) return true

        if (javaClass != other.javaClass) return false

        other as AbstractJpaPersistable<*>

        return if (null == this.id) false else this.id == other.id
    }

    override fun hashCode(): Int {
        return 31
    }

    override fun toString() = "Entity of type ${this.javaClass.name} with id: $id"

}