package compman.compsrv.service

interface ICommandProcessingService<StateType, CommandType, EventType> {
    fun apply(event: EventType, state: StateType?): Pair<StateType?, List<EventType>>
    fun batchApply(events: List<EventType>, state: StateType?): Pair<StateType?, List<EventType>> {
        return events.foldRight(state to emptyList()) { eventHolder, acc ->
            val pair = apply(eventHolder, acc.first)
            pair.first to (acc.second + pair.second)
        }
    }
    fun process(command: CommandType, state: StateType?): List<EventType>
}