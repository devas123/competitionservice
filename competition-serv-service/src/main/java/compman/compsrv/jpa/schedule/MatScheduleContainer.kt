package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.MatScheduleContainerDTO
import compman.compsrv.repository.FightCrudRepository
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@Embeddable
@Access(AccessType.FIELD)
class FightStartTimePair(
        @ManyToOne(optional = false)
        @JoinColumn(name = "FIGHT_ID")
        val fight: FightDescription,
        val fightNumber: Int,
        val startTime: Instant) : Serializable
@Entity
class MatScheduleContainer(
        @Transient
        var currentTime: Instant,
        var totalFights: Int,
        id: String,
        @ElementCollection
        @CollectionTable(
                name = "FIGHT_START_TIMES",
                joinColumns = [JoinColumn(name = "MAT_SCHEDULE_ID")]
        )
        @OrderColumn
        var fights: List<FightStartTimePair>,
        @Transient
        var pending: ArrayList<FightDescription>) : AbstractJpaPersistable<String>(id), Serializable {


    constructor(currentTime: Instant, id: String) : this(currentTime, 0, id, ArrayList(), ArrayList())
}
