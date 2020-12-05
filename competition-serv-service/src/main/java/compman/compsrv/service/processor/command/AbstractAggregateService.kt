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
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractAggregateService<AT : AbstractAggregate>(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : ValidatedExecutor<AT>(mapper, validators) {
    val log: Logger = LoggerFactory.getLogger(AbstractAggregateService::class.java)


    protected fun Either<CommandProcessingError, AggregateWithEvents<AT>>.unwrap(command: CommandDTO) =
        this.fold({ throw CommandProcessingException(it.message, command) }, { it })

    protected fun Either<EventApplicationError, AggregateWithEvents<AT>>.unwrap(event: EventDTO) =
        this.fold({ throw EventApplyingException(it.message, event) }, { it })

    private fun List<EventDTO>.setVersion(version: Long, agg: AT) =
        this.map(agg::enrichWithVersionAndNumber.curry()(version))

    protected abstract val commandsToHandlers: Map<CommandType, CommandExecutor<AT>>

    protected abstract val eventsToPayloads: Map<EventType, Class<out Payload>>

    fun processCommand(command: CommandDTO, rocksDBOperations: DBOperations): AggregateWithEvents<AT> {
        val aggregate = getAggregate(command, rocksDBOperations)
        return generateEventsFromAggregate(command, rocksDBOperations, aggregate)
    }

    protected abstract fun Payload.accept(aggregate: AT, event: EventDTO): AT

    fun applyEvent(aggregate: AT, event: EventDTO, rocksDBOperations: DBOperations, save: Boolean = true): AT {
        return executeValidated(event, eventsToPayloads.getValue(event.type)) { payload, e ->
            val updatedAggregate = payload.accept(aggregate, e)
            if (save) {
                saveAggregate(updatedAggregate, rocksDBOperations)
            }
            updatedAggregate
        }.fold({ throw EventApplyingException(it.message, event) }, { it })
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
        return aggWithEvents.first to aggWithEvents.second.mapIndexed { _, e -> e.setId(IDGenerator.uid()) }
            .setVersion(version, aggregate)
    }

    abstract fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): AT
    abstract fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): AT
    abstract fun saveAggregate(aggregate: AT, rocksDBOperations: DBOperations): AT
}