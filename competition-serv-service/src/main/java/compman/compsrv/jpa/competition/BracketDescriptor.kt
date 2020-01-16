package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.brackets.StageDescriptor
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.OneToMany

@Entity
class BracketDescriptor(id: String,
                        var competitionId: String,
                        @OneToMany(orphanRemoval = true)
                        @JoinColumn(name = "brackets_id")
                        var stages: MutableSet<StageDescriptor>?
) : AbstractJpaPersistable<String>(id)
