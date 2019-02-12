package compman.compsrv.model.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class EventDTO {
    private final long timestamp = System.currentTimeMillis();

    private String id = null;

    private String correlationId;

    private String competitionId;

    private String categoryId;

    private String matId;

    private EventType type;

    private Serializable payload;

    private Map<String, String> metadata;
}