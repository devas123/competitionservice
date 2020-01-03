package compman.compsrv.service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.REQUIRED)
interface ICommandProcessingService<CommandType, EventType, State> {

    fun apply(state: State, event: EventType, isBatch: Boolean = false): Pair<State, List<EventType>>

    fun batchApply(state: State, events: List<EventType>): Pair<State, List<EventType>> {
        return events.filter { !duplicateCheck(it) }.fold((state to emptyList())) { acc, eventHolder ->
            val newPair = apply(acc.first, eventHolder, isBatch = true)
            newPair.first to (acc.second + newPair.second)
        }
    }

    fun duplicateCheck(event: EventType): Boolean

    fun process(state: State, command: CommandType): List<EventType>
}