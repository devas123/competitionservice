package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.CategoryRestrictionType
import jdk.nashorn.internal.ir.annotations.Immutable
import javax.persistence.Entity
import javax.persistence.ManyToMany

@Entity
@Immutable
class CategoryRestriction(id: String,
                          var type: CategoryRestrictionType?,
                          var name: String?,
                          var minValue: String?,
                          var maxValue: String?,
                          var unit: String?,
                          @ManyToMany(mappedBy = "restrictions")
                          var categories: MutableSet<CategoryDescriptor>?)  : AbstractJpaPersistable<String>(id)