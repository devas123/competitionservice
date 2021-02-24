package compman.compsrv.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rocksdb")
public class RocksDBProperties {
    private String path = "tmp/rocksdb/db/test.db";

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
