package compman.compsrv.service.processor.command

import arrow.core.Either
import arrow.core.curry
import arrow.core.fix
import arrow.syntax.function.curried
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
import compman.compsrv.repository.RocksDBOperations
import compman.compsrv.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractAggregateService<AT : AbstractAggregate>(val mapper: ObjectMapper, val validators: List<PayloadValidator>) {
    val log: Logger = LoggerFactory.getLogger(AbstractAggregateService::class.java)
    inline fun <reified T : Payload> executeValidated(command: CommandDTO, payloadClass: Class<T>,
                                                      crossinline logic: (payload: T, com: CommandDTO) -> AggregatesWithEvents<AT>): Either<CommandProcessingError, AggregatesWithEvents<AT>> {
        val payload = mapper.getPayloadAs(command, payloadClass)!!
        return PayloadValidationRules
                .accumulateErrors { payload.validate(command, validators).fix() }
                .map { logic(payload, command) }
                .toEither()
                .mapLeft { CommandProcessingError.PayloadValidationFailed(it) }
    }

    inline fun <reified T : Payload> executeValidated(event: EventDTO, payloadClass: Class<T>,
                                                      crossinline logic: (payload: T, event: EventDTO) -> Unit) {
        val payload = mapper.getPayloadAs(event, payloadClass)!!
        PayloadValidationRules
                .accumulateErrors { payload.validate(event, validators).fix() }
                .map { logic(payload, event) }
                .toEither()
                .mapLeft { EventApplicationError.PayloadValidationFailed(it) }
    }

    protected fun createEvent(command: CommandDTO, eventType: EventType, payload: Any?) = mapper.createEvent(command, eventType, payload)
    protected fun Either<CommandProcessingError, AggregatesWithEvents<AT>>.unwrap(command: CommandDTO) = this.fold({ throw CommandProcessingException(it.message, command) }, { it })
    protected fun Either<EventApplicationError, AggregatesWithEvents<AT>>.unwrap(event: EventDTO) = this.fold({ throw EventApplyingException(it.message, event) }, { it })
    private fun List<EventDTO>.setVersion(version: Long, agg: AT) = this.map(agg::enrichWithVersionAndNumber.curry()(version))

    protected abstract val commandsToHandlers: Map<CommandType, CommandExecutor<AT>>

    fun processCommand(command: CommandDTO, rocksDBOperations: RocksDBOperations): AggregatesWithEvents<AT> {
        val aggregateList = getAggregate(command, rocksDBOperations)
        return aggregateList.fold({
            it.flatMap(this::generateEventsFromAggregate.curried()(command)(rocksDBOperations))
        }, this::generateEventsFromAggregate.curried()(command)(rocksDBOperations))
    }

    private fun generateEventsFromAggregate(command: CommandDTO, rocksDBOperations: RocksDBOperations, aggregate: AT): AggregatesWithEvents<AT> {
        val version = aggregate.getVersion()
        return commandsToHandlers[command.type]?.invoke(aggregate, rocksDBOperations, command)
                ?.map { events -> events.first to events.second.mapIndexed { _, e -> e.setId(IDGenerator.uid()) }.setVersion(version, aggregate) }
                ?: throw CommandProcessingException("Unknown command type: ${command.type}", command)
    }

    abstract fun getAggregate(command: CommandDTO, rocksDBOperations: RocksDBOperations): Either<List<AT>, AT>
    abstract fun getAggregate(event: EventDTO, rocksDBOperations: RocksDBOperations): AT

}