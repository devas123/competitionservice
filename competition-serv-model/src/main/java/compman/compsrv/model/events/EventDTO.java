package compman.compsrv.model.events;

import lombok.*;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
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
