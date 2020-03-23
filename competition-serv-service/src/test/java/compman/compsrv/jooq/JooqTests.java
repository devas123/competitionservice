package compman.compsrv.jooq;

import compman.compsrv.model.dto.brackets.AdditionalGroupSortingDescriptorDTO;
import compman.compsrv.model.dto.brackets.GroupSortDirection;
import compman.compsrv.model.dto.brackets.GroupSortSpecifier;
import compman.compsrv.model.dto.brackets.StageDescriptorDTO;
import compman.compsrv.model.dto.competition.*;
import compman.compsrv.model.dto.schedule.PeriodDTO;
import compman.compsrv.model.dto.schedule.ScheduleDTO;
import compman.compsrv.repository.JooqMappers;
import compman.compsrv.repository.JooqQueryProvider;
import compman.compsrv.repository.JooqRepository;
import compman.compsrv.service.AbstractGenerateServiceTest;
import compman.compsrv.service.CategoryGeneratorService;
import compman.compsrv.service.TestDataGenerationUtils;
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
            final AdditionalGroupSortingDescriptorDTO[] additionalGroupSortingDescriptorDTOS =new AdditionalGroupSortingDescriptorDTO[]{
                    new AdditionalGroupSortingDescriptorDTO()
                            .setGroupSortDirection(GroupSortDirection.ASC)
                            .setGroupSortSpecifier(GroupSortSpecifier.POINTS_DIFFERENCE),
                    new AdditionalGroupSortingDescriptorDTO()
                            .setGroupSortDirection(GroupSortDirection.DESC)
                            .setGroupSortSpecifier(GroupSortSpecifier.DIRECT_FIGHT_RESULT)
            };
            List<StageDescriptorDTO> stages = Collections.singletonList(testDataGenerationUtils.createStage(competitionId, categoryId, stageId, fights, additionalGroupSortingDescriptorDTOS));
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
    public void  testSaveSchedule() throws SQLException {
        String competitionId = "competitionId";
        long fightDuration = 8L;
        List<Pair<String, CategoryDescriptorDTO>> categories = Arrays.asList(
                new Pair<>("stage1", testDataGenerationUtils.category1(fightDuration)),
                new Pair<>("stage2", testDataGenerationUtils.category2(fightDuration)),
                new Pair<>("stage3", testDataGenerationUtils.category3(fightDuration)),
                new Pair<>("stage4", testDataGenerationUtils.category4(fightDuration)));
        int competitorNumbers = 10;
        List<Pair<String, List<FightDescriptionDTO>>> fights = categories.stream().map( cat ->
                new Pair<>(cat.getFirst(), testDataGenerationUtils.generateFilledFights(competitionId, cat.getSecond(), cat.getFirst(), competitorNumbers, BigDecimal.valueOf(fightDuration))))
                .collect(Collectors.toList());
        ScheduleDTO schedule = testDataGenerationUtils.generateSchedule(categories, fights, competitionId, competitorNumbers);
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             DSLContext dsl = DSL.using(conn, new Settings().withRenderNameStyle(RenderNameStyle.AS_IS))) {
            JooqRepository jooqRepository = createJooqRepository(dsl);
            CompetitionPropertiesDTO competitionPropertiesDTO = testDataGenerationUtils.createCompetitionPropertiesDTO(competitionId);
            jooqRepository.saveCompetitionState(new CompetitionStateDTO()
                    .setCategories(new CategoryStateDTO[]{})
                    .setId(competitionId)
                    .setProperties(competitionPropertiesDTO));
            List<CompetitorDTO> competitors = fights.stream().flatMap(f -> f.getSecond().stream())
                    .flatMap(f -> Arrays.stream(Optional.ofNullable(f.getScores()).orElse(new CompScoreDTO[] {})))
            .map(CompScoreDTO::getCompetitor)
            .filter(comp -> comp != null && comp.getId() != null)
            .collect(Collectors.toList());
            categories.forEach(cat -> jooqRepository.saveCategoryDescriptor(cat.getSecond(), competitionId));
            List<StageDescriptorDTO> stages = categories.stream().map(cat -> testDataGenerationUtils.createStage(competitionId, cat.getSecond().getId(), cat.getFirst(),
                    fights.stream().filter(p -> p.getFirst().equalsIgnoreCase(cat.getFirst())).findFirst().get().getSecond(), null)).collect(Collectors.toList());
            jooqRepository.saveStages(stages);
            jooqRepository.saveCompetitors(competitors);
            jooqRepository.saveFights(fights.stream().flatMap(p -> p.getSecond().stream()).collect(Collectors.toList()));
            jooqRepository.saveSchedule(schedule);
            List<PeriodDTO> periods = jooqRepository.fetchPeriodsByCompetitionId(competitionId).collectList().block();

            Assert.assertNotNull(periods);
            Assert.assertEquals(schedule.getPeriods().length, periods.size());
            for (PeriodDTO period : schedule.getPeriods()) {
                Assert.assertEquals(1, periods.stream().filter(p -> Objects.equals(p.getId(), period.getId()))
                        .peek(p -> {
                            Assert.assertEquals(period.getMats().length, p.getMats().length);
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
