package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.brackets.DistributionType
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.OneToMany

@Entity
class StageInputDescriptor(id: String,
                           var numberOfCompetitors: Int,
                           @OneToMany(orphanRemoval = true)
                           @Cascade(CascadeType.ALL)
                           @JoinColumn(name = "stage_input_id")
                           var selectors: MutableSet<CompetitorSelector>?,
                           var distributionType: DistributionType?): AbstractJpaPersistable<String>(id)