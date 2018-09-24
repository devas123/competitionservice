package compman.compsrv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cluster")
public class ClusterConfigurationProperties {
    private Zookeeper zookeeper = new Zookeeper();
    private String advertisedHost = "localhost";
    private Integer advertisedPort = 6359;
    private Boolean enableCluster = false;

    public Integer getAdvertisedPort() {
        return advertisedPort;
    }

    public void setAdvertisedPort(Integer advertisedPort) {
        this.advertisedPort = advertisedPort;
    }

    public Boolean getEnableCluster() {
        return enableCluster;
    }

    public void setEnableCluster(Boolean enableCluster) {
        this.enableCluster = enableCluster;
    }

    public String getAdvertisedHost() {
        return advertisedHost;
    }

    public void setAdvertisedHost(String advertisedHost) {
        this.advertisedHost = advertisedHost;
    }

    public Zookeeper getZookeeper() {
        return zookeeper;
    }

    public void setZookeeper(Zookeeper zookeeper) {
        this.zookeeper = zookeeper;
    }

    public static class Zookeeper {
        private String namespace = "/compservice";
        private String electionPath = "/election";
        private String workersPath = "/workers";
        private String connectionString = "localhost:2181";
        private Integer sessionTimeout = 5000;
        private Long connectTimeout = -1L;

        public String getWorkersPath() {
            return workersPath;
        }

        public void setWorkersPath(String workersPath) {
            this.workersPath = workersPath;
        }

        public Long getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Long connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getElectionPath() {
            return electionPath;
        }

        public void setElectionPath(String electionPath) {
            this.electionPath = electionPath;
        }

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }

        public Integer getSessionTimeout() {
            return sessionTimeout;
        }

        public void setSessionTimeout(Integer sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
        }
    }
}
