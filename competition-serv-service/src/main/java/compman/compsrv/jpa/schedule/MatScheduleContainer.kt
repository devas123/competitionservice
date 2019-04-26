package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.MatScheduleContainerDTO
import compman.compsrv.repository.FightCrudRepository
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.io.Serializable
import java.time.Instant
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
    fun toDTO(matId: String): FightStartTimePairDTO {
        return FightStartTimePairDTO()
                .setFightId(fight.id)
                .setFightNumber(fightNumber)
                .setStartTime(startTime)
                .setMatId(matId)
    }

    companion object {
        fun fromDTO(dto: FightStartTimePairDTO, fight: FightDescription) =
                FightStartTimePair(
                        fight = fight,
                        fightNumber = dto.fightNumber,
                        startTime = dto.startTime
                )
    }
}

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
        var fights: List<FightStartTimePair>,
        @Transient
        var pending: ArrayList<FightDescription>) : AbstractJpaPersistable<String>(id), Serializable {
    fun toDTO(): MatScheduleContainerDTO {
        return MatScheduleContainerDTO()
                .setTotalFights(totalFights)
                .setId(id)
                .setFights(fights.map { it.toDTO(id ?: "") }.toTypedArray())
    }

    constructor(currentTime: Instant, id: String) : this(currentTime, 0, id, ArrayList(), ArrayList())

    companion object {
        fun fromDTO(dto: MatScheduleContainerDTO, fightCrudRepository: FightCrudRepository) =
                MatScheduleContainer(Instant.now(), dto.totalFights, dto.id, dto.fights.map { FightStartTimePair.fromDTO(it, fightCrudRepository.getOne(it.fightId)) }, ArrayList())

        fun fromDTO(dto: MatScheduleContainerDTO, fights: List<FightDescription>) =
                MatScheduleContainer(Instant.now(), dto.totalFights, dto.id, dto.fights.map { fsp -> FightStartTimePair.fromDTO(fsp, fights.first { it.id == fsp.fightId }) }, ArrayList())
    }
}
