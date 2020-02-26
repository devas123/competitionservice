package compman.compsrv.jooq;

import com.compmanager.compservice.jooq.tables.FightDescription;
import com.compmanager.compservice.jooq.tables.records.FightDescriptionRecord;
import compman.compsrv.model.dto.brackets.*;
import compman.compsrv.model.dto.competition.*;
import compman.compsrv.repository.JooqMappers;
import compman.compsrv.repository.JooqQueryProvider;
import compman.compsrv.repository.JooqRepository;
import compman.compsrv.service.CategoryGeneratorService;
import compman.compsrv.service.fight.BracketsGenerateService;
import kotlin.Pair;
import org.jooq.DSLContext;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class JooqTests {
    static {
        LogManager.getLogManager().getLogger("").setLevel(Level.OFF);
    }

    private final BracketsGenerateService bracketsGenerateService = new BracketsGenerateService();
    private final String competitionId = "testCompetitionId";

    @Rule
    public PostgreSQLContainer postgres = new PostgreSQLContainer<>()
            .withInitScript("db/migration/V1.0__create_schema.sql")
            .withPassword("postgres")
            .withUsername("postgres");


    @Test
    public void testSaveDefaultCategories() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             DSLContext dsl = DSL.using(conn, new Settings().withRenderNameStyle(RenderNameStyle.AS_IS))) {
            JooqRepository jooqRepository = createJooqRepository(dsl);
            CategoryGeneratorService csg = new CategoryGeneratorService();
            List<CategoryDescriptorDTO> categories = csg.createDefaultBjjCategories(competitionId);
            categories.forEach(cat -> jooqRepository.saveCategoryDescriptor(cat, competitionId));

            Assert.assertEquals(categories.size(),
                    Objects.requireNonNull(jooqRepository.fetchCategoryStatesByCompetitionId(competitionId).collectList().block()).size());
        }
    }

    private JooqRepository createJooqRepository(DSLContext dsl) {
        return new JooqRepository(dsl, new JooqQueryProvider(dsl), new JooqMappers());
    }

    @Test
    public void testSaveStages() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             DSLContext dsl = DSL.using(conn, new Settings().withRenderNameStyle(RenderNameStyle.AS_IS))) {
            BigDecimal duration = BigDecimal.valueOf(8L);
            JooqRepository jooqRepository = createJooqRepository(dsl);
            CategoryGeneratorService csg = new CategoryGeneratorService();
            List<CategoryDescriptorDTO> categories = csg.createDefaultBjjCategories(competitionId);
            String categoryId = "categoryId";
            String stageId = "stageId";
            CategoryDescriptorDTO category = categories.get(0).setId(categoryId);
            CompetitionPropertiesDTO competitionPropertiesDTO = new CompetitionPropertiesDTO()
                    .setCompetitionName("Compname")
                    .setId(competitionId)
                    .setBracketsPublished(false)
                    .setCreationTimestamp(System.currentTimeMillis())
                    .setCreatorId("creatorId")
                    .setEmailNotificationsEnabled(false)
                    .setEmailTemplate("")
                    .setEndDate(Instant.now())
                    .setStartDate(Instant.now())
                    .setStatus(CompetitionStatus.CREATED)
                    .setTimeZone("UTC")
                    .setSchedulePublished(false);
            jooqRepository.saveCompetitionState(new CompetitionStateDTO()
                    .setCategories(new CategoryStateDTO[]{})
                    .setId(competitionId)
                    .setProperties(competitionPropertiesDTO));
            jooqRepository.saveCategoryDescriptor(category, competitionId);
            List<FightDescriptionDTO> fights = bracketsGenerateService.generateDoubleEliminationBracket(competitionId, categoryId, stageId, 50, duration);
            ArrayList<StageDescriptorDTO> stages = new ArrayList<>();
            final AdditionalGroupSortingDescriptorDTO[] additionalGroupSortingDescriptorDTOS =new AdditionalGroupSortingDescriptorDTO[]{
                    new AdditionalGroupSortingDescriptorDTO()
                            .setGroupSortDirection(GroupSortDirection.ASC)
                            .setGroupSortSpecifier(GroupSortSpecifier.POINTS_DIFFERENCE),
                    new AdditionalGroupSortingDescriptorDTO()
                            .setGroupSortDirection(GroupSortDirection.DESC)
                            .setGroupSortSpecifier(GroupSortSpecifier.DIRECT_FIGHT_RESULT)
            };
            stages.add(new StageDescriptorDTO()
                    .setId(stageId)
                    .setName("Name")
                    .setBracketType(BracketType.DOUBLE_ELIMINATION)
                    .setStageType(StageType.FINAL)
                    .setCategoryId(categoryId)
                    .setCompetitionId(competitionId)
                    .setHasThirdPlaceFight(false)
                    .setNumberOfFights(fights.size())
                    .setStageOrder(0)
                    .setStageStatus(StageStatus.APPROVED)
                    .setStageResultDescriptor(new StageResultDescriptorDTO()
                    .setId(stageId)
                    .setAdditionalGroupSortingDescriptors(additionalGroupSortingDescriptorDTOS))
                    .setGroupDescriptors(new GroupDescriptorDTO[]{
                            new GroupDescriptorDTO()
                                    .setId(stageId + "-group-" + UUID.randomUUID().toString())
                            .setName(stageId + "group-Name")
                            .setSize(25),
                            new GroupDescriptorDTO()
                                    .setId(stageId + "-group-" + UUID.randomUUID().toString())
                                    .setName(stageId + "group-Name1")
                                    .setSize(25)
                    }));
            jooqRepository.saveStages(stages);
            jooqRepository.saveGroupDescriptors(stages.stream().map(s -> new Pair<>(s.getId(), Arrays.asList(s.getGroupDescriptors())))
                    .collect(Collectors.toList()));
            jooqRepository.saveResultDescriptors(stages.stream().map(s -> new Pair<>(s.getId(), s.getStageResultDescriptor())).collect(Collectors.toList()));
            jooqRepository.saveFights(fights);

            List<FightDescriptionRecord> rawFights = jooqRepository.getDsl()
                    .selectFrom(FightDescription.FIGHT_DESCRIPTION).fetch();

            long count = jooqRepository.fightsCountByStageId(competitionId, stageId);
            Assert.assertEquals(fights.size(), count);

            List<StageDescriptorDTO> loadedStages = jooqRepository.fetchStagesForCategory(competitionId, categoryId).collectList().block();
            Assert.assertNotNull(loadedStages);
            Assert.assertEquals(stages.size(), loadedStages.size());
            List<FightDescriptionDTO> loadedFights = jooqRepository.fetchFightsByStageId(competitionId, stageId).collectList().block();
            Assert.assertNotNull(loadedFights);
            Assert.assertEquals(fights.size(), loadedFights.size());
            loadedStages.forEach(st -> {
                Assert.assertNotNull(st.getStageResultDescriptor());
                Assert.assertNotNull(st.getStageResultDescriptor().getAdditionalGroupSortingDescriptors());
                Assert.assertEquals(additionalGroupSortingDescriptorDTOS.length, st.getStageResultDescriptor().getAdditionalGroupSortingDescriptors().length);
                for (AdditionalGroupSortingDescriptorDTO additionalGroupSortingDescriptor : st.getStageResultDescriptor().getAdditionalGroupSortingDescriptors()) {
                    Assert.assertTrue(Arrays.stream(additionalGroupSortingDescriptorDTOS).anyMatch(g -> g.getGroupSortDirection() == additionalGroupSortingDescriptor.getGroupSortDirection()
                            && g.getGroupSortSpecifier() == additionalGroupSortingDescriptor.getGroupSortSpecifier()));
                }
                Assert.assertNotNull(st.getGroupDescriptors());
                Assert.assertEquals(2, st.getGroupDescriptors().length);
                Arrays.stream(st.getGroupDescriptors()).forEach(gr -> {
                    Assert.assertEquals(25, (int) gr.getSize());
                    Assert.assertNotNull(gr.getName());
                    Assert.assertNotNull(gr.getId());
                });

            });
        }
    }
}
