package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.MatScheduleContainerDTO
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.io.Serializable
import java.time.Instant
import java.time.ZoneId
import javax.persistence.*

@Embeddable
@Access(AccessType.FIELD)
class FightStartTimePair(
        @ManyToOne(optional = false)
        @JoinColumn(name = "FIGHT_ID")
        @OnDelete(action = OnDeleteAction.CASCADE)
        val fight: FightDescription,
        val fightNumber: Int,
        val startTime: Instant) : Serializable {
    fun toDTO(): FightStartTimePairDTO {
        return FightStartTimePairDTO()
                .setFight(fight.toDTO())
                .setFightNumber(fightNumber)
                .setStartTime(startTime)
    }

    companion object {
        fun fromDTO(dto: FightStartTimePairDTO) =
                FightStartTimePair(
                        fight = FightDescription.fromDTO(dto.fight),
                        fightNumber = dto.fightNumber,
                        startTime = dto.startTime
                )
    }
}

@Entity
class MatScheduleContainer(
        @Transient
        var currentTime: Instant,
        var currentFightNumber: Int,
        id: String,
        @ElementCollection
        @CollectionTable(
                name = "FIGHT_START_TIMES",
                joinColumns = [JoinColumn(name = "MAT_SCHEDULE_ID")]
        )
        var fights: List<FightStartTimePair>,
        @Transient
        var pending: ArrayList<FightDescription>,
        var timeZone: String) : AbstractJpaPersistable<String>(id), Serializable {
    fun toDTO(): MatScheduleContainerDTO {
        return MatScheduleContainerDTO()
                .setCurrentFightNumber(currentFightNumber)
                .setId(id)
                .setFights(fights.map { it.toDTO() }.toTypedArray())
    }

    constructor(currentTime: Instant, id: String) : this(currentTime, 0, id, ArrayList(), ArrayList(), ZoneId.systemDefault().id)

    companion object {
        fun fromDTO(dto: MatScheduleContainerDTO) =
                MatScheduleContainer(Instant.now(), dto.currentFightNumber, dto.id, dto.fights.map { FightStartTimePair.fromDTO(it) }, ArrayList(), dto.timeZone)
    }
}
