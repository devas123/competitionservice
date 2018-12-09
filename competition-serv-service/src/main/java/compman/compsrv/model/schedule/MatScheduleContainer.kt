package compman.compsrv.model.schedule

import compman.compsrv.model.competition.FightDescription
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.*
import javax.persistence.*
import kotlin.collections.ArrayList

@Embeddable
@Access(AccessType.FIELD)
data class FightStartTimePair(
        @ManyToOne(optional = false)
        @JoinColumn(name = "FIGHT_ID")
        @OnDelete(action = OnDeleteAction.CASCADE)
        val fight: FightDescription,
        val fightNumber: Int,
        val startTime: Date)

@Entity
class MatScheduleContainer(
        @Transient
        val currentTime: Date,
        var currentFightNumber: Int,
        @Id val matId: String,
        @ElementCollection
        @CollectionTable(
                name = "FIGHT_START_TIMES",
                joinColumns = [JoinColumn(name = "MAT_SCHEDULE_ID")]
        )
        var fights: List<FightStartTimePair>,
        @Transient
        val pending: ArrayList<FightDescription>) {
    constructor(currentTime: Date, matId: String) : this(currentTime, 0, matId, ArrayList(), ArrayList())
}
