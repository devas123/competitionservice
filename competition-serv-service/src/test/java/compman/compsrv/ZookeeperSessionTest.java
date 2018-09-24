package compman.compsrv;

import com.compman.starter.properties.KafkaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.config.ClusterConfiguration;
import compman.compsrv.config.ClusterConfigurationProperties;
import compman.compsrv.config.ClusterConfigurationProperties.Zookeeper;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.kafka.serde.CommandSerializer;
import compman.compsrv.kafka.topics.CompetitionServiceTopics;
import compman.compsrv.model.competition.*;
import compman.compsrv.model.es.commands.Command;
import compman.compsrv.model.es.commands.CommandType;
import compman.compsrv.repository.CategoryStateRepository;
import compman.compsrv.kafka.EmbeddedSingleNodeKafkaCluster;
import compman.compsrv.service.CategoryStateService;
import compman.compsrv.service.RestApi;
import compman.compsrv.service.ScheduleService;
import junit.framework.TestCase;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(value = RestApi.class, secure = false)
@ActiveProfiles("mongo-embed")
@ContextConfiguration(classes = {ZookeeperSessionTest.TestConfig.class}, initializers = ZookeeperSessionTest.RandomPortInitailizer.class)
@EnableConfigurationProperties({ClusterConfigurationProperties.class, KafkaProperties.class})
public final class ZookeeperSessionTest {

    private static ObjectMapper mapper = ObjectMapperFactory.INSTANCE.createObjectMapper();
    @Autowired
    private RestApi clusterManager;


    public static class RandomPortInitailizer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(@org.jetbrains.annotations.NotNull ConfigurableApplicationContext applicationContext) {
            try {
                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
                        "cluster.zookeeper.connection-string=" + CLUSTER.zookeeperConnect());

                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
                        "kafka.bootstrap-servers=" + CLUSTER.bootstrapServers());

                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext,
                        "server.port=" + randomFreeLocalPort());
            } catch (Exception ignore) {
            }
        }
    }

    @MockBean
    private CategoryStateRepository categoryStateRepository;

    private static final String LEADER_CHANGELOG_TOPIC = "leader-changelog";
    private static final String APPLICATION_ID = "test.app";
    private static final String COMPETITION1 = "Compeititon_1";
    private static final String COMPETITION2 = "Compeititon_2";

    @Autowired
    private MockMvc mvc;

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private static Properties createBrokerProperties() {
        Properties props = new Properties();
        props.setProperty("transaction.state.log.replication.factor", "1");
        return props;
    }

    @ClassRule
    public static final EmbeddedSingleNodeKafkaCluster CLUSTER = new EmbeddedSingleNodeKafkaCluster(createBrokerProperties());

    @BeforeClass
    public static void init() {
//        mapper.registerModule(new Jdk8Module());
//        mapper.registerModule(new KotlinModule());
        mapper.findAndRegisterModules();
    }

    @AfterClass
    public static void destroy() {
    }

    @Before
    public void tempCreate() throws IOException {
        temp.create();
    }

    @After
    public void deleteTempFolder() {
        temp.delete();
    }

    private static int randomFreeLocalPort() {
        return SocketUtils.findAvailableTcpPort();
    }

    private static final Logger log = LoggerFactory.getLogger(ZookeeperSessionTest.class);

    @NotNull
    public final TemporaryFolder getTemp() {
        return temp;
    }

    private static ClusterConfigurationProperties createClusterprops(int localPort) {
        ClusterConfigurationProperties clusterProps = new ClusterConfigurationProperties();
        clusterProps.setAdvertisedPort(localPort);
        clusterProps.setAdvertisedHost("localhost");
        clusterProps.setEnableCluster(true);
        Zookeeper zookeeper = clusterProps.getZookeeper();
        zookeeper.setWorkersPath("/workers");
        zookeeper.setElectionPath("/election");
        zookeeper.setConnectTimeout(3000L);
        zookeeper.setNamespace("/compservice");
        zookeeper.setSessionTimeout(30000);
        zookeeper.setConnectionString(CLUSTER.zookeeperConnect());
        return clusterProps;
    }

    private static KafkaProperties createKafkaProps(int localPort) {
        KafkaProperties kafkaProps = new KafkaProperties();
        kafkaProps.setLeaderChangelogTopic(LEADER_CHANGELOG_TOPIC);
        String bootstrapServers = CLUSTER.bootstrapServers();
        kafkaProps.setBootstrapServers(bootstrapServers);
        kafkaProps.getStreamProperties().setProperty(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        kafkaProps.getStreamProperties().setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaProps.getStreamProperties().setProperty(StreamsConfig.APPLICATION_SERVER_CONFIG, String.valueOf(localPort));
        kafkaProps.getStreamProperties().setProperty(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        kafkaProps.getDefaultTopicOptions().setPartitions(1);
        kafkaProps.getDefaultTopicOptions().setReplicationFactor((short) 1);
        kafkaProps.getProducer()
                .getProperties()
                .setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        kafkaProps.getProducer()
                .getProperties()
                .setProperty(ProducerConfig.ACKS_CONFIG, "all");
        kafkaProps.getProducer()
                .getProperties()
                .setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        kafkaProps.getProducer()
                .getProperties()
                .setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return kafkaProps;
    }

    @Ignore
    @Test
    public final void testZookeeperSession() throws Exception {
//        Mockito.when(categoryStateRepository.getLatestSnapshot(Mockito.anyString())).thenReturn(null);
//        Mockito.doNothing().when(categoryStateRepository).saveSnapshot(Mockito.any(CategoryState.class));

        int port1 = randomFreeLocalPort();
        int port2 = randomFreeLocalPort();
        int port3 = randomFreeLocalPort();
        KafkaProperties kafkaProps = createKafkaProps(port1);

//        ZookeeperSession zk1 = new ZookeeperSession(createClusterprops(port1), createKafkaProps(port1), categoryStateRepository, eventRepository, competitionStateService);
//        ZookeeperSession zk2 = new ZookeeperSession(createClusterprops(port2), createKafkaProps(port2), categoryStateRepository, eventRepository, competitionStateService);
//        ZookeeperSession zk3 = new ZookeeperSession(createClusterprops(port3), createKafkaProps(port3), categoryStateRepository, eventRepository, competitionStateService);

        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(CLUSTER.zookeeperConnect())
                .sessionTimeoutMs(10000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 10))
                .build();
        curatorFramework.start();
        curatorFramework.blockUntilConnected(5, TimeUnit.SECONDS);

        Properties adminProps = new Properties();
        adminProps.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, CLUSTER.bootstrapServers());

        AdminClient adminClient = KafkaAdminClient.create(adminProps);

        Set<String> topics = adminClient.listTopics().names().get();

        while (topics == null || !topics.contains(CompetitionServiceTopics.COMPETITIONS_COMMANDS_TOPIC_NAME)) {
            log.info("Waiting for global competition commands topics to be created. Current topics are: " + topics);
            topics = adminClient.listTopics().names().get();
            sleep(1000);
        }

//        Assert.assertEquals(4, curatorFramework.getChildren().forPath(zk1.getWorkersPath()).size());
//        Assert.assertEquals(4, curatorFramework.getChildren().forPath(zk1.getElectionPath()).size());
//
//        log.info("" + curatorFramework.getChildren().forPath(zk1.getWorkersPath()));
//        log.info("" + curatorFramework.getChildren().forPath(zk1.getElectionPath()));
//        log.info(new String(curatorFramework.getData().forPath(zk1.getNodeDescriptionNode())));
//        log.info(new String(curatorFramework.getData().forPath(zk2.getNodeDescriptionNode())));
//        log.info(new String(curatorFramework.getData().forPath(zk3.getNodeDescriptionNode())));
//        log.info(new String(curatorFramework.getData().forPath(zk3.getNodeDescriptionNode().substring(0, zk3.getNodeDescriptionNode().lastIndexOf("/")) + "/compsrv_0000000000")));

        KafkaProducer<String, Command> producer = new KafkaProducer<>(kafkaProps.getProducer().getProperties(), new StringSerializer(), new CommandSerializer());
        CompetitionProperties pr1 = new CompetitionProperties(COMPETITION1, COMPETITION1, "valera@protas.ru");
        CompetitionProperties pr2 = new CompetitionProperties(COMPETITION2, COMPETITION2, "valera@protas.ru");
        producer.send(new ProducerRecord<>(CompetitionServiceTopics.COMPETITIONS_COMMANDS_TOPIC_NAME, COMPETITION1, new Command(COMPETITION1, CommandType.CREATE_COMPETITION_COMMAND,
                "", mapper.convertValue(new CompetitionState(COMPETITION1, new CategoryState[3], pr1), LinkedHashMap.class))));
        producer.send(new ProducerRecord<>(CompetitionServiceTopics.COMPETITIONS_COMMANDS_TOPIC_NAME, COMPETITION2, new Command(COMPETITION2, CommandType.CREATE_COMPETITION_COMMAND,
                "", mapper.convertValue(new CompetitionState(COMPETITION2, new CategoryState[3], pr1), LinkedHashMap.class))));

        producer.flush();

        sleep(1000);

        topics = adminClient.listTopics().names().get();

        String comp2commandsTopic = CompetitionServiceTopics.CATEGORIES_COMMANDS_TOPIC_NAME;

        while (topics == null || !topics.contains(comp2commandsTopic)) {
            log.info("Waiting for competition topics to be created. Current topics are: " + topics);
            topics = adminClient.listTopics().names().get();
            sleep(1000);
        }
        log.info("Topics created: " + topics);

//        log.info("compsrv_000000000: " + new String(curatorFramework.getData().forPath(zk3.getElectionPath() + "/compsrv_0000000000")));
//        log.info(zk1.getElectionNode() + ": " + new String(curatorFramework.getData().forPath(zk1.getElectionNode())));
//        log.info(zk2.getElectionNode() + ": " + new String(curatorFramework.getData().forPath(zk2.getElectionNode())));
//        log.info(zk3.getElectionNode() + ": " + new String(curatorFramework.getData().forPath(zk3.getElectionNode())));

        Category[] categories = {
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "FEMALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Light", new BigDecimal("76.0")), BeltType.BLACK, new BigDecimal(10)),
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "MALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Feather", new BigDecimal("76.0")), BeltType.BLUE, new BigDecimal(10)),
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "FEMALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Heavy", new BigDecimal("76.0")), BeltType.WHITE, new BigDecimal(10)),
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "MALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Light Feather", new BigDecimal("76.0")), BeltType.PURPLE, new BigDecimal(10)),
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "FEMALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Super Heavy", new BigDecimal("76.0")), BeltType.BROWN, new BigDecimal(10)),
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "MALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Ultra Heavy", new BigDecimal("76.0")), BeltType.BLACK, new BigDecimal(10)),
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "FEMALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Rooster", new BigDecimal("76.0")), BeltType.WHITE, new BigDecimal(10)),
                new Category(BjjAgeDivisions.INSTANCE.getADULT(), "MALE", COMPETITION2, UUID.randomUUID().toString(), new Weight("Light", new BigDecimal("76.0")), BeltType.PURPLE, new BigDecimal(10))};
        for (int i = 0; i < 30; i++) {
            Category cat = categories[i % categories.length];
            producer.send(new ProducerRecord<>(comp2commandsTopic, COMPETITION2, new Command(COMPETITION2, CommandType.ADD_CATEGORY_COMMAND, cat.getCategoryId(), mapper.convertValue(cat, LinkedHashMap.class))));
            sleep(10);
        }

        producer.flush();

        sleep(5000);

        byte[] categoriesResult = mvc.perform(get("/cluster/store/categories")
                .param("competitionId", COMPETITION2)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        log.info("Categories: " + new String(categoriesResult, StandardCharsets.UTF_8));

        Arrays.stream(categories).forEach(cat ->
        {
            byte[] result = new byte[0];
            try {
                result = mvc.perform(get("/cluster/store/categorystate")
                        .param("competitionId", COMPETITION2)
                        .param("categoryId", cat.getCategoryId())
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsByteArray();
            } catch (Exception e) {
                log.error("Exception: ", e);
            }

            log.info(new String(result, StandardCharsets.UTF_8));
            TestCase.assertNotNull(result);
        });

//        sleep(100000000000000L);
        producer.close(1, TimeUnit.SECONDS);
        adminClient.close(1, TimeUnit.SECONDS);
        sleep(1000);
//        zk1.close();
//        sleep(300);
//        zk2.close();
//        sleep(300);
//        zk3.close();
    }

    @Autowired
    private CategoryStateService competitionStateService;

    public static void main(String[] args) throws Exception {
        CLUSTER.start();
//        int localPort = randomFreeLocalPort();
//        KafkaProperties kafkaProps = createKafkaProps(localPort);
//
//        CategoryStateRepository categoryStateRepository = Mockito.mock(CategoryStateRepository.class);
//        CategoryStateService competitionStateService = Mockito.mock(CategoryStateService.class);
//        ZookeeperSession zk1 = new ZookeeperSession(createClusterprops(localPort), kafkaProps, categoryStateRepository, competitionStateService);
        Runtime.getRuntime().addShutdownHook(new Thread(CLUSTER::stop));
//        Runtime.getRuntime().addShutdownHook(new Thread(zk1::close));
        log.info("Zookeeper Connect: " + CLUSTER.zookeeperConnect());
        log.info("Bootstrap servers: " + CLUSTER.bootstrapServers());
        Path gatewayConfigFile = Paths.get("gateway-service", "src", "main", "resources", "application-dev.properties");
        Path compsrvConfigFile = Paths.get("competitionservice", "competition-serv-service", "src", "main", "resources", "application-dev.properties");
        Files.write(gatewayConfigFile, Arrays.asList(("cluster.zookeeper.connection-string=" + CLUSTER.zookeeperConnect()), ("kafka.bootstrap-servers=" + CLUSTER.bootstrapServers())), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(compsrvConfigFile, Arrays.asList(("cluster.zookeeper.connection-string=" + CLUSTER.zookeeperConnect()), ("kafka.bootstrap-servers=" + CLUSTER.bootstrapServers())), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//        Files.write(configFile, , StandardOpenOption.APPEND);
        Thread.sleep(10000000);
        CLUSTER.stop();
    }

    @Configuration
    @Import({ClusterConfiguration.class,
            CategoryStateService.class,
            ScheduleService.class,
            RestApi.class})
    static class TestConfig {

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplateBuilder().build();
        }
    }
}
