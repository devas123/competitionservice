package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import java.math.BigDecimal
import javax.persistence.*


@Entity(name = "category_descriptor")
class CategoryDescriptor(
        @Column(columnDefinition = "varchar(255)")
        var competitionId: String,
        var name: String?,
        @ManyToMany
        @JoinTable(
                name = "category_descriptor_restriction",
                joinColumns = [JoinColumn(name = "category_descriptor_id")],
                inverseJoinColumns = [ JoinColumn(name = "category_restriction_id") ]
        )
        @Cascade(CascadeType.SAVE_UPDATE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH)
        var restrictions: MutableSet<CategoryRestriction>?,
        @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
        var competitors: MutableSet<Competitor>?,
        id: String,
        var fightDuration: BigDecimal) : AbstractJpaPersistable<String>(id)