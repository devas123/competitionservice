package compman.compsrv.model.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class EventDTO extends MessageInfo {
    private final long timestamp = System.currentTimeMillis();

    private Long version;

    private Long localEventNumber;

    private EventType type;

}
