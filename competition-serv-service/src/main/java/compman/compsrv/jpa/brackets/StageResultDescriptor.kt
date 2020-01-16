package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.Competitor
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import org.hibernate.annotations.Immutable
import javax.persistence.*

@Entity
class StageResultDescriptor(id: String?,
                            var name: String?,
                            @OrderColumn(name = "competitor_place")
                            @ManyToMany
                            @JoinTable(name = "STAGE_COMPETITOR_RESULT",
                                    joinColumns =
                                    [JoinColumn(name = "COMPETITOR_RESULT_COMPETITOR_ID", referencedColumnName = "ID")],
                                    inverseJoinColumns =
                                    [JoinColumn(name = "STAGE_RESULT_DESCRIPTOR_ID", referencedColumnName = "ID")]
                            )
                            @Cascade(CascadeType.ALL)
                            var competitorResults: MutableList<CompetitorResult>?) : AbstractJpaPersistable<String>(id)


@Entity
@Immutable
class CompetitorResult(id: String?,
                       @ManyToOne(optional = false)
                       @JoinColumn(name = "competitor_id", nullable = false, updatable = false)
                       var competitor: Competitor?,
                       var points: Int?,
                       var round: Int?,
                       var place: Int?,
                       var groupId: String?,
                       @ManyToMany(mappedBy = "competitorResults")
                       var stageResultDescriptors: MutableSet<StageResultDescriptor>) : AbstractJpaPersistable<String>(id)