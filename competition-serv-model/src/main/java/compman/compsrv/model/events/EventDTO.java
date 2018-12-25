package compman.compsrv.model.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class EventDTO {
    private final long timestamp = System.currentTimeMillis();

    private String id = null;

    private final String correlationId;

    private final String competitionId;

    private final String categoryId;

    private final String matId;

    private final EventType type;

    private final Serializable payload;

    private Map<String, String> metadata;
}
