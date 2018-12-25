package compman.compsrv.jpa.competition

import javax.persistence.Id


data class Academy(val name: String,
                   private val coaches: List<String>?,
                   val created: Long?) {
    constructor(name: String, coaches: List<String>) : this(name, coaches, System.currentTimeMillis())

    @Id
    val id: String = name.toLowerCase().replace(" ", "")

    fun addCoach(coach: String) = copy(coaches = if (coaches == null) listOf(coach) else coaches + coach)
}