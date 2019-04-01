package compman.compsrv;

import com.compman.starter.properties.KafkaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.config.ClusterConfiguration;
import compman.compsrv.config.ClusterConfigurationProperties;
import compman.compsrv.jpa.competition.CompetitionProperties;
import compman.compsrv.jpa.competition.CompetitionState;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.kafka.EmbeddedSingleNodeKafkaCluster;
import compman.compsrv.kafka.serde.CommandSerializer;
import compman.compsrv.kafka.topics.CompetitionServiceTopics;
import compman.compsrv.model.commands.CommandDTO;
import compman.compsrv.model.commands.CommandType;
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO;
import compman.compsrv.service.processor.event.CategoryEventProcessor;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public final TemporaryFolder getTemp() {
        return temp;
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
        int port1 = randomFreeLocalPort();
       KafkaProperties kafkaProps = createKafkaProps(port1);

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

        while (topics == null || !topics.contains(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME)) {
            log.info("Waiting for global competition commands topics to be created. Current topics are: " + topics);
            topics = adminClient.listTopics().names().get();
            sleep(1000);
        }

        KafkaProducer<String, CommandDTO> producer = new KafkaProducer<>(kafkaProps.getProducer().getProperties(), new StringSerializer(), new CommandSerializer());
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        CompetitionState pr1 = new CompetitionState(id1, new CompetitionProperties(id1, COMPETITION1, ""));
        CompetitionState pr2 = new CompetitionState(id2, new CompetitionProperties(id2, COMPETITION2, ""));
        producer.send(new ProducerRecord<>(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, COMPETITION1, new CommandDTO()
                .setId(id1)
                .setCompetitionId(COMPETITION1)
                .setType(CommandType.CREATE_COMPETITION_COMMAND)
                .setPayload(mapper.convertValue(pr1, LinkedHashMap.class))));
        producer.send(new ProducerRecord<>(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, COMPETITION2, new CommandDTO()
                .setId(id1)
                .setCompetitionId(COMPETITION2)
                .setType(CommandType.CREATE_COMPETITION_COMMAND)
                .setPayload(mapper.convertValue(pr2, LinkedHashMap.class))));

        producer.flush();

        sleep(1000);

        topics = adminClient.listTopics().names().get();

        String comp2commandsTopic = CompetitionServiceTopics.CATEGORY_COMMANDS_TOPIC_NAME;

        while (topics == null || !topics.contains(comp2commandsTopic)) {
            log.info("Waiting for competition topics to be created. Current topics are: " + topics);
            topics = adminClient.listTopics().names().get();
            sleep(1000);
        }
        log.info("Topics created: " + topics);

        CategoryDescriptorDTO[] categories = {};
//        for (int i = 0; i < 30; i++) {
//            int ind = 0;
//            if (categories.length == 0) {
//                ind = i;
//            } else {
//                ind = i % categories.length;
//            }
//            CategoryDescriptor cat = categories[i % (categories.length == 0 ? 1 : categories.length)];
//            producer.send(new ProducerRecord<>(comp2commandsTopic, COMPETITION2, new Command(COMPETITION2, CommandType.ADD_CATEGORY_COMMAND, cat.getId(), mapper.writeValueAsBytes(cat))));
//            sleep(10);
//        }

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
                        .param("categoryId", cat.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsByteArray();
            } catch (Exception e) {
                log.error("Exception: ", e);
            }

            log.info(new String(result, StandardCharsets.UTF_8));
            TestCase.assertNotNull(result);
        });

        producer.close(1, TimeUnit.SECONDS);
        adminClient.close(1, TimeUnit.SECONDS);
        sleep(1000);
    }

    public static void main(String[] args) throws Exception {
        CLUSTER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(CLUSTER::stop));
        log.info("Zookeeper Connect: " + CLUSTER.zookeeperConnect());
        log.info("Bootstrap servers: " + CLUSTER.bootstrapServers());
        Thread.sleep(10000000);
        CLUSTER.stop();
    }

    @Configuration
    @Import({ClusterConfiguration.class,
            CategoryEventProcessor.class,
            ScheduleService.class,
            RestApi.class})
    static class TestConfig {

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplateBuilder().build();
        }
    }
}
