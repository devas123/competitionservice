package compman.compsrv;


import com.fasterxml.jackson.databind.ObjectMapper;
import compman.compsrv.config.ClusterConfiguration;
import compman.compsrv.config.ClusterConfigurationProperties;
import compman.compsrv.json.ObjectMapperFactory;
import compman.compsrv.kafka.EmbeddedSingleNodeKafkaCluster;
import compman.compsrv.service.RestApi;
import compman.compsrv.service.schedule.ScheduleService;
import kafka.server.KafkaConfig;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RestTemplate;
import properties.KafkaProperties;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Ignore
@RunWith(SpringRunner.class)
@WebMvcTest(value = RestApi.class)
@ActiveProfiles("mongo-embed")
@ContextConfiguration(classes = {ZookeeperSessionTest.TestConfig.class}, initializers = ZookeeperSessionTest.RandomPortInitailizer.class)
@EnableConfigurationProperties({ClusterConfigurationProperties.class, KafkaProperties.class})
public final class ZookeeperSessionTest {

    private static final ObjectMapper mapper = ObjectMapperFactory.INSTANCE.createObjectMapper();

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

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private static Properties createBrokerProperties() {
        Properties props = new Properties();
        props.setProperty("transaction.state.log.replication.factor", "1");
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            props.setProperty(KafkaConfig.AdvertisedListenersProp(), "EXTERNAL://195.168.0.1:61384,INTERNAL://localhost:61383");
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


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final String runFile = args[0];
        try {
            CLUSTER.start();
        } catch (Exception e) {
            log.error("Error while starting kafka.", e);
            Runtime.getRuntime().addShutdownHook(new Thread(CLUSTER::stop));
            log.info("Zookeeper Connect: " + CLUSTER.zookeeperConnect());
            log.info("Bootstrap servers: " + CLUSTER.bootstrapServers());

        }

        ReentrantLock lock = new ReentrantLock(true);
        Condition cond = lock.newCondition();

        ExecutorService s = ForkJoinPool.commonPool();
        Future<Boolean> waiting = s.submit(() -> {
            boolean t = false;
            lock.lock();
            try {
                while (Files.exists(Path.of(runFile))) {
                    try {
                        t = cond.await(500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                lock.unlock();
            }
            return t;
        });
        waiting.get();
        log.info("Stopping.");
        CLUSTER.stop();
    }

    @Configuration
    @Import({ClusterConfiguration.class,
            ScheduleService.class,
            RestApi.class})
    static class TestConfig {

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplateBuilder().build();
        }
    }
}
