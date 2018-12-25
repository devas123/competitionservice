package compman.compsrv.repository


import compman.compsrv.jpa.competition.CategoryDescriptor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CategoryDescriptorCrudRepository : JpaRepository<CategoryDescriptor, String>