package compman.compsrv.model.exceptions;

import compman.compsrv.model.events.EventDTO;

public final class EventApplyingException extends Exception {
    private final EventDTO event;

    public final EventDTO getEvent() {
        return this.event;
    }

    public EventApplyingException(String message, EventDTO event) {
        super(message);
        this.event = event;
    }
}