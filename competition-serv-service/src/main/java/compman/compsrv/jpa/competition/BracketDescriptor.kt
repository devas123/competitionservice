package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.brackets.StageDescriptor
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.OneToMany

@Entity
class BracketDescriptor(id: String,
                        var competitionId: String,
                        @OneToMany(orphanRemoval = true)
                        @JoinColumn(name = "brackets_id")
                        @Cascade(CascadeType.ALL)
                        var stages: MutableSet<StageDescriptor>?
) : AbstractJpaPersistable<String>(id)
