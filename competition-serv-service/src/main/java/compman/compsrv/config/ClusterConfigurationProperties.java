package compman.compsrv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "cluster")
public class ClusterConfigurationProperties {
    private String advertisedHost = null;
    private Integer advertisedPort = 6359;
    private Boolean enableCluster = false;
    private List<String> clusterSeed = new ArrayList<>();

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

    public List<String> getClusterSeed() {
        return clusterSeed;
    }

    public void setClusterSeed(List<String> clusterSeed) {
        this.clusterSeed = clusterSeed;
    }
}
