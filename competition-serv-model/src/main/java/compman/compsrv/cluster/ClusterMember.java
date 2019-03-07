package compman.compsrv.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ClusterMember {
    private String id;
    private String uri;
    private String host;
    private String port;
}
