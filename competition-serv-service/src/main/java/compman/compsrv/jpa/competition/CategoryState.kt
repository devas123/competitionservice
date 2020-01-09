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
                    @OneToOne(optional = false, fetch = FetchType.LAZY)
                    @PrimaryKeyJoinColumn
                    @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DELETE, CascadeType.REMOVE)
                    var category: CategoryDescriptor?,
                    var status: CategoryStatus?,
                    @OneToOne(optional = false, fetch = FetchType.LAZY)
                    @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DELETE, CascadeType.REMOVE)
                    @PrimaryKeyJoinColumn
                    var brackets: BracketDescriptor?) : AbstractJpaPersistable<String>(id) {

}
