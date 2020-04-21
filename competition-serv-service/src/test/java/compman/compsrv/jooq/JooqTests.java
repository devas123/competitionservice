package compman.compsrv.jooq;

import compman.compsrv.model.dto.brackets.*;
import compman.compsrv.model.dto.competition.*;
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO;
import compman.compsrv.model.dto.schedule.PeriodDTO;
import compman.compsrv.model.dto.schedule.ScheduleDTO;
import compman.compsrv.repository.JooqMappers;
import compman.compsrv.repository.JooqQueryProvider;
import compman.compsrv.repository.JooqRepository;
import compman.compsrv.service.AbstractGenerateServiceTest;
import compman.compsrv.service.CategoryGeneratorService;
import compman.compsrv.service.TestDataGenerationUtils;
import compman.compsrv.service.fight.BracketsGenerateService;
import compman.compsrv.service.fight.FightsService;
import kotlin.Pair;
import org.jooq.DSLContext;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class JooqTests {
    static {
        LogManager.getLogManager().getLogger("").setLevel(Level.OFF);
    }

    private final BracketsGenerateService bracketsGenerateService = new BracketsGenerateService();
    private final String competitionId = "testCompetitionId";
    private final TestDataGenerationUtils testDataGenerationUtils = new TestDataGenerationUtils(bracketsGenerateService);

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
            CompetitionPropertiesDTO competitionPropertiesDTO = testDataGenerationUtils.createCompetitionPropertiesDTO(competitionId);
            jooqRepository.saveCompetitionState(new CompetitionStateDTO()
                    .setCategories(new CategoryStateDTO[]{})
                    .setId(competitionId)
                    .setProperties(competitionPropertiesDTO));
            jooqRepository.saveCategoryDescriptor(category, competitionId);
            List<FightDescriptionDTO> fights = bracketsGenerateService.generateDoubleEliminationBracket(competitionId, categoryId, stageId, 50, duration);
            AbstractGenerateServiceTest.Companion.checkDoubleEliminationLaws(fights, 32);
            final AdditionalGroupSortingDescriptorDTO[] additionalGroupSortingDescriptorDTOS = new AdditionalGroupSortingDescriptorDTO[]{
                    new AdditionalGroupSortingDescriptorDTO()
                            .setGroupSortDirection(GroupSortDirection.ASC)
                            .setGroupSortSpecifier(GroupSortSpecifier.POINTS_DIFFERENCE),
                    new AdditionalGroupSortingDescriptorDTO()
                            .setGroupSortDirection(GroupSortDirection.DESC)
                            .setGroupSortSpecifier(GroupSortSpecifier.DIRECT_FIGHT_RESULT)
            };
            List<StageDescriptorDTO> stages = Collections.singletonList(testDataGenerationUtils.createGroupStage(competitionId, categoryId, stageId, additionalGroupSortingDescriptorDTOS));
            jooqRepository.saveStages(stages);
            jooqRepository.saveGroupDescriptors(stages.stream().map(s -> new Pair<>(s.getId(), Arrays.asList(s.getGroupDescriptors())))
                    .collect(Collectors.toList()));
            jooqRepository.saveResultDescriptors(stages.stream().map(s -> new Pair<>(s.getId(), s.getStageResultDescriptor())).collect(Collectors.toList()));
            jooqRepository.saveFights(fights);

            long count = jooqRepository.fightsCountByStageId(competitionId, stageId);
            Assert.assertEquals(fights.size(), count);

            List<StageDescriptorDTO> loadedStages = jooqRepository.fetchStagesForCategory(competitionId, categoryId).collectList().block();
            Assert.assertNotNull(loadedStages);
            Assert.assertEquals(stages.size(), loadedStages.size());
            List<FightDescriptionDTO> loadedFights = jooqRepository.fetchFightsByStageId(competitionId, stageId).collectList().block();
            Assert.assertNotNull(loadedFights);
            Assert.assertEquals(fights.size(), loadedFights.size());
            AbstractGenerateServiceTest.Companion.checkDoubleEliminationLaws(loadedFights, 32);
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

    @Test
    public void testSaveSchedule() throws SQLException {
        String competitionId = "competitionId";
        long fightDuration = 8L;
        List<Pair<String, CategoryDescriptorDTO>> categories = Arrays.asList(
                new Pair<>("stage1", testDataGenerationUtils.category1(fightDuration)),
                new Pair<>("stage2", testDataGenerationUtils.category2(fightDuration)),
                new Pair<>("stage3", testDataGenerationUtils.category3(fightDuration)),
                new Pair<>("stage4", testDataGenerationUtils.category4(fightDuration)));
        int competitorNumbers = 10;
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             DSLContext dsl = DSL.using(conn, new Settings().withRenderNameStyle(RenderNameStyle.AS_IS))) {
            JooqRepository jooqRepository = createJooqRepository(dsl);
            CompetitionPropertiesDTO competitionPropertiesDTO = testDataGenerationUtils.createCompetitionPropertiesDTO(competitionId);
            jooqRepository.saveCompetitionState(new CompetitionStateDTO()
                    .setCategories(new CategoryStateDTO[]{})
                    .setId(competitionId)
                    .setProperties(competitionPropertiesDTO));
            List<Pair<StageDescriptorDTO, List<FightDescriptionDTO>>> stagesToFights = categories.stream().map(cat -> {
                List<CompetitorDTO> competitors = FightsService.Companion.generateRandomCompetitorsForCategory(competitorNumbers, 10, cat.getSecond(), competitionId);
                StageDescriptorDTO stage = testDataGenerationUtils.createSingleEliminationStage(competitionId, cat.getSecond().getId(), cat.getFirst(), competitorNumbers);
                jooqRepository.saveCompetitors(competitors);
                return new Pair<>(stage, testDataGenerationUtils.generateFilledFights(competitionId, cat.getSecond(),
                        stage, competitors, BigDecimal.valueOf(fightDuration)));
            })
                    .collect(Collectors.toList());
            ScheduleDTO schedule = testDataGenerationUtils.generateSchedule(categories, stagesToFights, competitionId, competitorNumbers);
            categories.forEach(cat -> jooqRepository.saveCategoryDescriptor(cat.getSecond(), competitionId));
            List<StageDescriptorDTO> stages = new ArrayList<>();
            for (Pair<StageDescriptorDTO, List<FightDescriptionDTO>> fight : stagesToFights) {
                StageDescriptorDTO first = fight.getFirst();
                stages.add(first);
            }
            jooqRepository.saveStages(stages);
            jooqRepository.saveResultDescriptors(stages.stream().map(s -> new Pair<>(s.getId(), s.getStageResultDescriptor())).collect(Collectors.toList()));
            jooqRepository.saveFights(stagesToFights.stream().flatMap(p -> p.getSecond().stream()).collect(Collectors.toList()));
            jooqRepository.saveSchedule(schedule);
            List<PeriodDTO> periods = jooqRepository.fetchPeriodsByCompetitionId(competitionId).collectList().block();
            List<MatDescriptionDTO> mats = jooqRepository.fetchMatsByCompetitionId(competitionId, true).collectList().block();

            Assert.assertNotNull(periods);
            Assert.assertNotNull(mats);
            Assert.assertEquals(schedule.getPeriods().length, periods.size());
            Assert.assertEquals(schedule.getMats().length, mats.size());
            for (PeriodDTO period : schedule.getPeriods()) {
                Assert.assertEquals(1, periods.stream().filter(p -> Objects.equals(p.getId(), period.getId()))
                        .peek(p -> {
                            Assert.assertEquals(period.getScheduleEntries().length, p.getScheduleEntries().length);
                            Assert.assertEquals(period.getScheduleRequirements().length, p.getScheduleRequirements().length);
                            Assert.assertEquals(period.getEndTime(), p.getEndTime());
                            Assert.assertEquals(period.getStartTime(), p.getStartTime());
                            Assert.assertEquals(period.getIsActive(), p.getIsActive());
                            Assert.assertEquals(period.getName(), p.getName());
                            if (period.getRiskPercent() != null && p.getRiskPercent() != null) {
                                Assert.assertEquals(0, period.getRiskPercent().compareTo(p.getRiskPercent()));
                            } else {
                                Assert.assertEquals(period.getRiskPercent(), p.getRiskPercent());
                            }
                            Assert.assertEquals(period.getTimeBetweenFights(), p.getTimeBetweenFights());
                        }).count());
            }

        }
    }
}
