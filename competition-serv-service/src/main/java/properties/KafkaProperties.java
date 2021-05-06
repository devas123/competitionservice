package properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

@ConfigurationProperties("kafka")
public class KafkaProperties {
    private String leaderChangelogTopic = "compservice-leader-changelog";
    private String bootstrapServers;
    private Consumer consumer = new Consumer();
    private Producer producer = new Producer();
    private Properties streamProperties = new Properties();
    private DefaultTopicOptions defaultTopicOptions = new DefaultTopicOptions();

    public String getLeaderChangelogTopic() {
        return leaderChangelogTopic;
    }

    public void setLeaderChangelogTopic(String leaderChangelogTopic) {
        this.leaderChangelogTopic = leaderChangelogTopic;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Producer getProducer() {
        return producer;
    }

    public void setProducer(Producer producer) {
        this.producer = producer;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public Properties getStreamProperties() {
        return streamProperties;
    }

    public void setStreamProperties(Properties streamProperties) {
        this.streamProperties = streamProperties;
    }

    public DefaultTopicOptions getDefaultTopicOptions() {
        return defaultTopicOptions;
    }

    public void setDefaultTopicOptions(DefaultTopicOptions defaultTopicOptions) {
        this.defaultTopicOptions = defaultTopicOptions;
    }

    public static class Topic {
        private String name;
        private List<Integer> partitions = Collections.emptyList();

        public List<Integer> getPartitions() {
            return partitions;
        }

        public void setPartitions(List<Integer> partitions) {
            this.partitions = partitions;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Consumer {
        private List<Topic> topics = Collections.emptyList();
        private Properties properties = new Properties();

        public Properties getProperties() {
            return properties;
        }

        public void setProperties(Properties properties) {
            this.properties = properties;
        }

        public List<Topic> getTopics() {
            return topics;
        }

        public void setTopics(List<Topic> topics) {
            this.topics = topics;
        }
    }

    public static class Producer {
        private Topic topic = new Topic();

        private Properties properties = new Properties();

        public Properties getProperties() {
            return properties;
        }

        public void setProperties(Properties properties) {
            this.properties = properties;
        }


        public Topic getTopic() {
            return topic;
        }

        public void setTopic(Topic topic) {
            this.topic = topic;
        }
    }

    public static class DefaultTopicOptions {
        private int partitions = 2;
        private short replicationFactor = 2;

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int partitions) {
            this.partitions = partitions;
        }

        public short getReplicationFactor() {
            return replicationFactor;
        }

        public void setReplicationFactor(short replicationFactor) {
            this.replicationFactor = replicationFactor;
        }
    }
}