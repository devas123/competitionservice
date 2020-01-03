package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.model.dto.competition.CategoryStatus
import compman.compsrv.service.ScheduleService
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import javax.persistence.*

@Entity(name = "category_state")
class CategoryState(id: String,
                    @ManyToOne(fetch = FetchType.LAZY)
                    @JoinColumn(name = "competition_id", nullable = false)
                    var competition: CompetitionState?,
                    @OneToOne(optional = false, fetch = FetchType.LAZY)
                    @Cascade(CascadeType.SAVE_UPDATE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH)
                    @PrimaryKeyJoinColumn
                    var category: CategoryDescriptor?,
                    var status: CategoryStatus?,
                    @OneToOne(fetch = FetchType.LAZY)
                    @Cascade(CascadeType.ALL)
                    @PrimaryKeyJoinColumn
                    var brackets: BracketDescriptor?) : AbstractJpaPersistable<String>(id)
