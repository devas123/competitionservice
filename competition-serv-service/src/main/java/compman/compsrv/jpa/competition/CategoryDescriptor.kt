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
        var sportsType: String,
        @ManyToOne(fetch = FetchType.LAZY)
        @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.PERSIST)
        @JoinColumn(name = "age_id")
        var ageDivision: AgeDivision,
        @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
        var competitors: MutableSet<Competitor>?,
        var gender: String,
        @ManyToOne(fetch = FetchType.LAZY)
        @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.PERSIST)
        @JoinColumn(name = "weight_id")
        var weight: Weight,
        var beltType: String,
        id: String,
        var fightDuration: BigDecimal) : AbstractJpaPersistable<String>(id) {
}