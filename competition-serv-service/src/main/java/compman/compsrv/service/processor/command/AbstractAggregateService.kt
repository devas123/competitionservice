package compman.compsrv.service.processor.command

import arrow.core.Either
import arrow.core.curry
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.errors.EventApplicationError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.event.IEventProcessor
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractAggregateService<AT : AbstractAggregate>(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : ValidatedExecutor<AT>(mapper, validators) {
    val log: Logger = LoggerFactory.getLogger(AbstractAggregateService::class.java)

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

//        fun createErrorEvent(event: EventDTO, payload: ErrorEventPayload): EventDTO = EventDTO().apply {
//            id = IDGenerator.uid()
//            categoryId = event.categoryId
//            correlationId = event.correlationId
//            competitionId = event.competitionId
//            competitorId = event.competitorId
//            version = event.version
//            localEventNumber = event.localEventNumber
//            matId = event.matId
//            type = EventType.ERROR_EVENT
//            setPayload(payload)
//        }

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

        inline fun <reified T : Payload>  getPayloadAs(event: EventDTO): T? {
            return event.payload as? T
        }

        inline fun <reified T : Payload> getPayloadAs(command: CommandDTO): T? {
            return command.payload as? T
        }
    }

    private fun List<EventDTO>.setVersion(version: Long, agg: AT) =
        this.map(agg::enrichWithVersionAndNumber.curry()(version))

    protected abstract val commandsToHandlers: Map<CommandType, CommandExecutor<AT>>
    protected abstract val eventsToProcessors: Map<CommandType, IEventProcessor<AT>>

    fun processCommand(command: CommandDTO, rocksDBOperations: DBOperations): AggregateWithEvents<AT> {
        val aggregate = getAggregate(command, rocksDBOperations)
        return generateEventsFromAggregate(command, rocksDBOperations, aggregate)
    }

//    protected abstract fun Payload.accept(aggregate: AT, event: EventDTO): AT

    fun applyEvent(aggregate: AT, event: EventDTO, rocksDBOperations: DBOperations, save: Boolean = true): AT {
        val updatedAggregate =  if (event.type?.payloadClass != null) {
            executeValidated(event) { payload: Payload, e ->
                payload.accept(aggregate, e)
            }.fold({ throw EventApplyingException(it.message, event) }, { it })
        } else {
            TODO()
        }
        if (save) {
            saveAggregate(updatedAggregate, rocksDBOperations)
        }
        return updatedAggregate
    }

    fun applyEvents(aggregate: AT, events: List<EventDTO>, rocksDBOperations: DBOperations): AT {
        val updatedAggregate = events.fold(aggregate) { acc, event -> applyEvent(acc, event, rocksDBOperations, false) }
        saveAggregate(updatedAggregate, rocksDBOperations)
        return updatedAggregate
    }

    private fun generateEventsFromAggregate(
        command: CommandDTO,
        rocksDBOperations: DBOperations,
        aggregate: AT
    ): AggregateWithEvents<AT> {
        val version = aggregate.getVersion()
        val aggWithEvents = commandsToHandlers[command.type]?.invoke(aggregate, rocksDBOperations, command)
            ?: throw CommandProcessingException("Command handler not implemented for type ${command.type}", command)
        return aggWithEvents.first to aggWithEvents.second.mapIndexed { _, e -> e.apply { id = IDGenerator.uid() } }
            .setVersion(version, aggregate)
    }

    abstract fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): AT
    abstract fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): AT
    abstract fun saveAggregate(aggregate: AT, rocksDBOperations: DBOperations): AT
}