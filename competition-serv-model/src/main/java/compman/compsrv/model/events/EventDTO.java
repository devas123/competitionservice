package compman.compsrv.model.events;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.Instant;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class EventDTO extends MessageInfo {
    private final Instant timestamp = Instant.now();

    private Long version;

    private Long localEventNumber;

    private EventType type;

}
