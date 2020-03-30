package compman.compsrv;

import com.compman.starter.properties.KafkaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.config.ClusterConfiguration;
import compman.compsrv.config.ClusterConfigurationProperties;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.kafka.EmbeddedSingleNodeKafkaCluster;
import compman.compsrv.service.RestApi;
import compman.compsrv.service.schedule.ScheduleService;
import compman.compsrv.service.processor.event.CategoryEventProcessor;
import kafka.server.KafkaConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;

@Ignore
@RunWith(SpringRunner.class)
@WebMvcTest(value = RestApi.class)
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
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            props.setProperty(KafkaConfig.AdvertisedListenersProp(), "EXTERNAL://" + ip + ":61384,INTERNAL://localhost:61383");
            props.setProperty(KafkaConfig.ListenersProp(), "EXTERNAL://" + ip + ":61384,INTERNAL://localhost:61383");
            props.setProperty(KafkaConfig.ListenerSecurityProtocolMapProp(), "EXTERNAL:PLAINTEXT,INTERNAL:PLAINTEXT");
            props.setProperty(KafkaConfig.InterBrokerListenerNameProp(), "INTERNAL");
            props.setProperty(KafkaConfig.TransactionsTopicMinISRProp(), "1");
            props.setProperty(KafkaConfig.TransactionsTopicReplicationFactorProp(), "1");
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
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

    public static void main(String[] args) throws Exception {
        CLUSTER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(CLUSTER::stop));
        log.info("Zookeeper Connect: " + CLUSTER.zookeeperConnect());
        log.info("Bootstrap servers: " + CLUSTER.bootstrapServers());
        Thread.sleep(10000000);
        log.info("Stopiing.");
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
