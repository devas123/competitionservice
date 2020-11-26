package compman.compsrv.jooq;

import compman.compsrv.model.dto.brackets.AdditionalGroupSortingDescriptorDTO;
import compman.compsrv.model.dto.brackets.GroupSortDirection;
import compman.compsrv.model.dto.brackets.GroupSortSpecifier;
import compman.compsrv.model.dto.brackets.StageDescriptorDTO;
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
import compman.compsrv.service.fight.GroupStageGenerateService;
import kotlin.Pair;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jooq.DSLContext;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootTest
public class JooqTests {
    private final String dbFile = "jooqTests.db";
    private Connection conn;
    private DSLContext dsl;
    private JooqRepository jooqRepository;

    private static final Logger log = LoggerFactory.getLogger(JooqTests.class);
    private final BracketsGenerateService bracketsGenerateService = new BracketsGenerateService();
    private final String competitionId = "testCompetitionId";
    private final TestDataGenerationUtils testDataGenerationUtils = new TestDataGenerationUtils(bracketsGenerateService, new GroupStageGenerateService());

    @Before
    public void setUp() throws SQLException, IOException {
        final SQLiteConfig config = new SQLiteConfig();
        config.setCacheSize(10000);
        config.setReadOnly(false);
        Files.createFile(Paths.get(dbFile));
        String sqLiteUrl = "jdbc:sqlite:" + dbFile;
        conn = config.createConnection(sqLiteUrl);
        dsl = DSL.using(conn, new Settings().withRenderNameStyle(RenderNameStyle.AS_IS));
        jooqRepository = createJooqRepository(dsl);
        FluentConfiguration flyWayConfig = new FluentConfiguration()
                .dataSource(sqLiteUrl, null, null)
                .locations("db/migration")
                .baselineOnMigrate(true);
        Flyway flyway = new Flyway(flyWayConfig);
        MigrationInfoService migrationInfoService = flyway.info();
        MigrationInfo[] migrationInfos = migrationInfoService.all();
        for (MigrationInfo migrationInfo : migrationInfos) {
            String version = migrationInfo.getVersion().toString();
            String state = migrationInfo.getState().isApplied()+"";
            log.info(String.format("Is target version %s applied? %s", version, state));
        }
        flyway.clean();
        flyway.migrate();
    }

    @After
    public void tearDown() throws SQLException {
        if (conn != null) {
            conn.close();
        }
        if (dsl != null) {
            dsl.close();
        }
        try {
            Files.delete(Paths.get(dbFile));
        } catch (IOException e) {
            log.info("Error when deleting db file.", e);
        }
    }

    @Test
    public void testSaveDefaultCategories() {
        CategoryGeneratorService csg = new CategoryGeneratorService();
            List<CategoryDescriptorDTO> categories = csg.createDefaultBjjCategories(competitionId);
            categories.forEach(cat -> jooqRepository.saveCategoryDescriptor(cat, competitionId));
            Assert.assertEquals(categories.size(),
                    Objects.requireNonNull(jooqRepository.fetchCategoryStatesByCompetitionId(competitionId).collectList().block()).size());
    }

    private JooqRepository createJooqRepository(DSLContext dsl) {
        return new JooqRepository(dsl, new JooqQueryProvider(dsl), new JooqMappers());
    }

    @Test
    public void testSaveStages() {
        BigDecimal duration = BigDecimal.valueOf(8L);
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
        List<StageDescriptorDTO> stages = Collections.singletonList(testDataGenerationUtils.createGroupStage(competitionId, categoryId, stageId, additionalGroupSortingDescriptorDTOS, Arrays.asList(25, 25)));
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
        Set<String> ids = new HashSet<>();
        for (FightDescriptionDTO fight : fights) {
            ids.add(fight.getId());
        }
        for (FightDescriptionDTO loadedFight : loadedFights) {
            if (ids.remove(loadedFight.getId())) {
                log.info("Fight was loaded: {} ", loadedFight);
            }
        }
        for (FightDescriptionDTO fight : fights) {
            if (ids.contains(fight.getId())) {
                log.error("Fight was not loaded: {} ", fight);
            }
        }
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

    @Test
    public void testSaveSchedule() {
        String competitionId = "competitionId";
        long fightDuration = 8L;
        List<Pair<String, CategoryDescriptorDTO>> categories = Arrays.asList(
                new Pair<>("stage1", testDataGenerationUtils.category1()),
                new Pair<>("stage2", testDataGenerationUtils.category2()),
                new Pair<>("stage3", testDataGenerationUtils.category3()),
                new Pair<>("stage4", testDataGenerationUtils.category4()));
        int competitorNumbers = 10;
        JooqRepository jooqRepository = createJooqRepository(dsl);
        CompetitionPropertiesDTO competitionPropertiesDTO = testDataGenerationUtils.createCompetitionPropertiesDTO(competitionId);
        jooqRepository.saveCompetitionState(new CompetitionStateDTO()
                .setCategories(new CategoryStateDTO[]{})
                .setId(competitionId)
                .setProperties(competitionPropertiesDTO));
        List<Pair<StageDescriptorDTO, List<FightDescriptionDTO>>> stagesToFights = categories.stream().map(cat -> {
            List<CompetitorDTO> competitors = FightsService.Companion.generateRandomCompetitorsForCategory(competitorNumbers, 10, cat.getSecond().getId(), competitionId);
            StageDescriptorDTO stage = testDataGenerationUtils.createSingleEliminationStage(competitionId, cat.getSecond().getId(), cat.getFirst(), competitorNumbers);
            jooqRepository.saveCompetitors(competitors);
            return new Pair<>(stage, testDataGenerationUtils.generateFilledFights(competitionId, cat.getSecond(),
                    stage, competitors, BigDecimal.valueOf(fightDuration)));
        })
                .collect(Collectors.toList());
        ScheduleDTO schedule = testDataGenerationUtils.generateSchedule(categories, stagesToFights, competitionId, competitorNumbers).getA();
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
        List<MatDescriptionDTO> mats = jooqRepository.fetchMatsByCompetitionId(competitionId).collectList().block();

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
