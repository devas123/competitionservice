package compman.compsrv.service

interface ICommandProcessingService<CommandType, EventType> {
    fun apply(event: EventType): List<EventType>
    fun batchApply(events: List<EventType>): List<EventType> {
        return events.foldRight(emptyList()) { eventHolder, acc ->
            (acc + apply(eventHolder))
        }
    }
    fun process(command: CommandType): List<EventType>
}