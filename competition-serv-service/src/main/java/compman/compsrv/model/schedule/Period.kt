package compman.compsrv.model.schedule

import compman.compsrv.model.competition.CategoryDescriptor
import javax.persistence.*

@Entity
data class Period(@Id val id: String,
                  val name: String,
                  @ElementCollection
                  @CollectionTable(
                          name = "SCHEDULE_ENTRIES",
                          joinColumns = [JoinColumn(name = "PERIOD_ID")]
                  )
                  val schedule: List<ScheduleEntry>,
                  @OneToMany(orphanRemoval = true)
                  @JoinColumn(name="PERIOD_ID", nullable = true)
                  val categories: List<CategoryDescriptor>,
                  val startTime: String,
                  val numberOfMats: Int,
                  @OneToMany(orphanRemoval = true)
                  @JoinColumn(name="PERIOD_ID", nullable = false)
                  val fightsByMats: List<MatScheduleContainer>?)