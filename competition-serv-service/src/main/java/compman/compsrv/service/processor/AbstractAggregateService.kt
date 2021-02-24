package compman.compsrv.service.processor

import arrow.core.curry
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.util.IDGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractAggregateService<AT : AbstractAggregate> {
    val log: Logger = LoggerFactory.getLogger(AbstractAggregateService::class.java)

    protected fun <T> Result<T>.getOrLogAndNull(): T? =
        this.getOrElse { log.error("Error in try catch block: ", it); null }

    companion object {
        fun createErrorEvent(command: CommandDTO, payload: ErrorEventPayload): EventDTO = EventDTO().apply {
            id = IDGenerator.uid()
            categoryId = command.categoryId
            correlationId = command.correlationId
            competitionId = command.competitionId
            competitorId = command.competitorId
            matId = command.matId
            type = EventType.ERROR_EVENT
            setPayload(payload)
        }


        fun createErrorEvent(command: CommandDTO, error: String?): EventDTO =
            createErrorEvent(command, ErrorEventPayload(error, command.id))


        fun createEvent(command: CommandDTO, type: EventType, payload: Payload?): EventDTO =
            EventDTO().apply {
                categoryId = command.categoryId
                correlationId = command.correlationId
                competitionId = command.competitionId
                competitorId = command.competitorId
                matId = command.matId
                setType(type)
                setPayload(payload)
            }

        inline fun <reified T : Payload> getPayloadAs(event: EventDTO): T? {
            return event.payload as? T
        }

        inline fun <reified T : Payload> getPayloadAs(command: CommandDTO): T? {
            return command.payload as? T
        }
    }

    private fun List<EventDTO>.setVersion(version: Long, agg: AT?) =
        agg?.let {
            this.map(agg::enrichWithVersionAndNumber.curry()(version))
        } ?: this

    protected abstract val commandsToHandlers: Map<CommandType, CommandExecutor<AT>>
    protected abstract val eventsToProcessors: Map<EventType, IEventHandler<AT>>
    protected abstract fun isAggregateDeleted(event: EventDTO): Boolean

    fun processCommand(command: CommandDTO, rocksDBOperations: DBOperations): AggregateWithEvents<AT> {
        log.info("Process command {}", command)
        val aggregate = getAggregate(command, rocksDBOperations)
        return generateEventsFromAggregate(command, rocksDBOperations, aggregate)
    }

//    protected abstract fun Payload.accept(aggregate: AT, event: EventDTO): AT

    fun applyEvent(aggregate: AT?, event: EventDTO, rocksDBOperations: DBOperations, save: Boolean = true): AT? {
        log.info("Apply event {}, save: {}", event, save)
        val newAggregate = eventsToProcessors[event.type]?.applyEvent(aggregate, event, rocksDBOperations)
            ?: throw EventApplyingException("Event handler not implemented for type ${event.type}", event)
        if (save && !isAggregateDeleted(event)) {
            newAggregate.inctementVersion()
            saveAggregate(newAggregate, rocksDBOperations)
        }
        return newAggregate
    }

    fun applyEvents(aggregate: AT?, events: List<EventDTO>, rocksDBOperations: DBOperations): AT? {
        val updatedAggregate = events.fold(aggregate) { acc, event -> applyEvent(acc, event, rocksDBOperations, false) }
        val save = events.fold(true) { acc, event -> acc && !isAggregateDeleted(event) }
        if (save && updatedAggregate != null) {
            updatedAggregate.inctementVersionBy(events.size)
            saveAggregate(updatedAggregate, rocksDBOperations)
        }
        return updatedAggregate
    }

    private fun generateEventsFromAggregate(
        command: CommandDTO,
        rocksDBOperations: DBOperations,
        aggregate: AT?
    ): AggregateWithEvents<AT> {
        val version = aggregate?.version() ?: 0
        val aggWithEvents = commandsToHandlers[command.type]?.invoke(aggregate, rocksDBOperations, command)
            ?: throw CommandProcessingException("Command handler not implemented for type ${command.type}", command)
        return aggWithEvents.first to aggWithEvents.second.mapIndexed { _, e -> e.apply { id = IDGenerator.uid() } }
            .setVersion(version, aggregate)
    }

    abstract fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): AT?
    abstract fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): AT?
    abstract fun saveAggregate(aggregate: AT?, rocksDBOperations: DBOperations): AT?
}