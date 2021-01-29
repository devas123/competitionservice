package compman.compsrv.service.processor

import compman.compsrv.errors.show
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddCategoryPayload
import compman.compsrv.model.commands.payload.AddCompetitorPayload
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition.*
import compman.compsrv.repository.RocksDBRepository
import compman.compsrv.service.processor.saga.AddCompetitor
import compman.compsrv.service.processor.saga.ChangeCompetitorCategory
import compman.compsrv.service.processor.saga.log
import compman.compsrv.util.IDGenerator
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("offline")
abstract class AbstractCompetitionLogicTestBase {

    @Autowired
    lateinit var repo: RocksDBRepository
    @Autowired
    lateinit var changeCompetitorCategory: ChangeCompetitorCategory
    @Autowired
    lateinit var addCompetitorSaga: AddCompetitor
    @Autowired
    lateinit var delegatingAggregateService: DelegatingAggregateService

    val log: Logger = LoggerFactory.getLogger(SagaApplicationTests::class.java)


    val competitionId = "competitionId"
    val categoryId = "categoryId"
    val newCategoryId = "newCategoryId"
    val competitorId = "competitorId"

    fun initCompetitionState() {
        val dbOperations = repo.getOperations()
        val createCompetition = CommandDTO().apply {
            id = IDGenerator.uid()
            this.categoryId = this@AbstractCompetitionLogicTestBase.categoryId
            this.competitionId = this@AbstractCompetitionLogicTestBase.competitionId
            this.competitorId = this@AbstractCompetitionLogicTestBase.competitorId
            type = CommandType.CREATE_COMPETITION_COMMAND
            payload = CreateCompetitionPayload().apply {
                this.properties = CompetitionPropertiesDTO()
                    .setId(competitionId)
                    .setCompetitionName("test")
                this.reginfo = RegistrationInfoDTO()
            }
        }

        val competitionCreated = delegatingAggregateService.getAggregateService(createCompetition)
            .processCommand(createCompetition, dbOperations)
        delegatingAggregateService.applyEvents(competitionCreated.first, competitionCreated.second, dbOperations)

        val addCategory = CommandDTO().apply {
            id = IDGenerator.uid()
            this.categoryId = this@AbstractCompetitionLogicTestBase.categoryId
            this.competitionId = this@AbstractCompetitionLogicTestBase.competitionId
            this.competitorId = this@AbstractCompetitionLogicTestBase.competitorId
            type = CommandType.ADD_CATEGORY_COMMAND
            payload = AddCategoryPayload().apply {
                this.category = CategoryDescriptorDTO().setId(this@AbstractCompetitionLogicTestBase.categoryId).setName("TestCategory")
                    .setRestrictions(arrayOf(
                        CategoryRestrictionDTO().setId("rid1").setMaxValue("10").setMinValue("5").setType(
                            CategoryRestrictionType.Range).setName("Age")))
            }
        }
        val addCategoryNew = CommandDTO().apply {
            id = IDGenerator.uid()
            this.categoryId = this@AbstractCompetitionLogicTestBase.newCategoryId
            this.competitionId = this@AbstractCompetitionLogicTestBase.competitionId
            this.competitorId = this@AbstractCompetitionLogicTestBase.competitorId
            type = CommandType.ADD_CATEGORY_COMMAND
            payload = AddCategoryPayload().apply {
                this.category = CategoryDescriptorDTO().setId(this@AbstractCompetitionLogicTestBase.newCategoryId).setName("TestCategoryNew")
                    .setRestrictions(arrayOf(
                        CategoryRestrictionDTO().setId("rid2").setMaxValue("10").setMinValue("5").setType(
                            CategoryRestrictionType.Range).setName("Age2")))
            }
        }

        val categoryAdded = delegatingAggregateService.getAggregateService(addCategory)
            .processCommand(addCategory, dbOperations)
        delegatingAggregateService.applyEvents(categoryAdded.first, categoryAdded.second, dbOperations)
        val categoryAddedNew = delegatingAggregateService.getAggregateService(addCategoryNew)
            .processCommand(addCategoryNew, dbOperations)
        delegatingAggregateService.applyEvents(categoryAddedNew.first, categoryAddedNew.second, dbOperations)

        val addCompetitor = CommandDTO().apply {
            id = IDGenerator.uid()
            this.categoryId = this@AbstractCompetitionLogicTestBase.categoryId
            this.competitionId = this@AbstractCompetitionLogicTestBase.competitionId
            this.competitorId = this@AbstractCompetitionLogicTestBase.competitorId
            type = CommandType.ADD_COMPETITOR_COMMAND
            payload = AddCompetitorPayload().apply {
                this.competitor = CompetitorDTO()
                    .setId(this@AbstractCompetitionLogicTestBase.competitorId)
                    .setEmail("testemail")
                    .setFirstName("firstname")
                    .setLastName("lastname")
                    .setCategories(arrayOf(this@AbstractCompetitionLogicTestBase.categoryId))
            }
        }

        val saga = addCompetitorSaga.createSaga(dbOperations, addCompetitor)
        saga.map { it.log(log) }
        val events = addCompetitorSaga.runSaga(dbOperations, saga)
        log.info(events.mapLeft { it.show() }.toString())
        assertTrue { dbOperations.categoryExists(categoryId) }
        assertTrue { dbOperations.categoryExists(newCategoryId) }
        assertTrue { dbOperations.competitionExists(competitionId) }
        assertTrue { dbOperations.competitorExists(competitorId) }
        val competitor = dbOperations.getCompetitor(competitorId)
        val category = dbOperations.getCategory(categoryId)
        val newCategory = dbOperations.getCategory(newCategoryId)
        assertNotNull(competitor.competitorDTO.categories)
        assertTrue { competitor.competitorDTO.categories.contains(categoryId) }
        assertTrue { category.numberOfCompetitors == 1 }
        assertTrue { newCategory.numberOfCompetitors == 0 }
    }
}