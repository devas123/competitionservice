package compman.compsrv.repository


import compman.compsrv.jpa.competition.CategoryRestriction
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(propagation = Propagation.SUPPORTS)
interface CategoryRestrictionCrudRepository : JpaRepository<CategoryRestriction, String>