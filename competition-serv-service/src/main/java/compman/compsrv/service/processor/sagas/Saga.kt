package compman.compsrv.service.processor.sagas

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.either.monad.monad
import arrow.core.extensions.id.monad.monad
import arrow.free.Free
import arrow.free.foldMap
import arrow.higherkind
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.RocksDBOperations
import compman.compsrv.service.processor.command.*
import org.slf4j.Logger

@higherkind
sealed class SagaStepA<out A> : SagaStepAOf<A> {
    data class ProcessCommand(val commandDTO: CommandDTO) : SagaStepA<List<EventDTO>>()
    data class ApplyEvent(val event: EventDTO) : SagaStepA<List<EventDTO>>()
    data class ApplyEvents(val events: List<EventDTO>) : SagaStepA<List<EventDTO>>()
    data class Fold<A>(val saga: SagaStep<A>, val ifSuccess: (List<EventDTO>) -> SagaStep<A>, val ifError: (CommandProcessingError) -> SagaStep<A>) : SagaStepA<A>()
    data class And<A>(val a: SagaStep<A>, val b: SagaStep<A>) : SagaStepA<A>()
    data class ExitWithError(val error: CommandProcessingError) : SagaStepA<CommandProcessingError>()
    data class ReturnEvents(val events: List<EventDTO>) : SagaStepA<List<EventDTO>>()
}

class ForSagaStep private constructor() {
    companion object
}


typealias SagaStepAOf<A> = Kind<ForSagaStep, A>

@Suppress("NOTHING_TO_INLINE")
inline fun <A> SagaStepAOf<A>.fix(): SagaStepA<A> =
        this as SagaStepA<A>

typealias SagaStep<A> = Free<ForSagaStep, A>

fun processCommand(commandDTO: CommandDTO): SagaStep<List<EventDTO>> = Free.liftF(SagaStepA.ProcessCommand(commandDTO))
fun applyEvent(event: EventDTO): SagaStep<List<EventDTO>> = Free.liftF(SagaStepA.ApplyEvent(event))
fun applyEvents(events: List<EventDTO>): SagaStep<List<EventDTO>> = Free.liftF(SagaStepA.ApplyEvents(events))
fun error(err: CommandProcessingError): SagaStep<CommandProcessingError> = Free.liftF(SagaStepA.ExitWithError(err))
fun returnEvents(events: List<EventDTO>): SagaStep<List<EventDTO>> = Free.liftF(SagaStepA.ReturnEvents(events))
inline fun <reified A> and(a: SagaStep<A>, b: SagaStep<A>): SagaStep<A> = Free.liftF(SagaStepA.And(a, b))
inline fun <reified A> foldSagas(saga: SagaStep<A>, noinline ifSuccess: (List<EventDTO>) -> SagaStep<A>, noinline ifError: (CommandProcessingError) -> SagaStep<A>): SagaStep<A> = Free.liftF(SagaStepA.Fold(saga, ifSuccess, ifError))
inline fun <reified A> SagaStep<A>.step(noinline ifSuccess: (List<EventDTO>) -> SagaStep<A>, noinline ifError: (CommandProcessingError) -> SagaStep<A>) = foldSagas(this, ifSuccess, ifError)

fun sagaInterpreterEither(rocksDBOperations: RocksDBOperations, aggregateServiceFactory: AggregateServiceFactory) = SagaExecutionFunctor(rocksDBOperations, aggregateServiceFactory)

fun <A> SagaStep<A>.failFast(rocksDBOperations: RocksDBOperations, aggregateServiceFactory: AggregateServiceFactory): Either<CommandProcessingError, A> {
    return foldMap(sagaInterpreterEither(rocksDBOperations, aggregateServiceFactory), Either.monad()).fix()
}

fun <A> SagaStep<A>.log(log: Logger): Id<A> {
    return foldMap(LoggingInterpreter(log), Id.monad()).fix()
}



@Suppress("UNCHECKED_CAST")
class SagaExecutionFunctor(private val rocksDBOperations: RocksDBOperations,  private val aggregateServiceFactory: AggregateServiceFactory) : FunctionK<ForSagaStep, EitherPartialOf<CommandProcessingError>> {
    override fun <A> invoke(fa: Kind<ForSagaStep, A>): EitherOf<CommandProcessingError, A> {
        return kotlin.runCatching {
            when (val saga = fa.fix()) {
                is SagaStepA.ProcessCommand -> {
                    Either.right(executeInAppropriateService(saga.commandDTO, rocksDBOperations, aggregateServiceFactory))
                }
                is SagaStepA.ApplyEvent -> {
                    aggregateServiceFactory.getAggregateService(saga.event).getAggregate(saga.event, rocksDBOperations).applyEvent(saga.event, rocksDBOperations).right()
                }
                is SagaStepA.ApplyEvents -> {
                    if (saga.events.isEmpty()) {
                        Either.left(CommandProcessingError.GenericError("No events to apply."))
                    } else {
                        val agg = aggregateServiceFactory.getAggregateService(saga.events[0])
                        agg.getAggregate(saga.events[0], rocksDBOperations).applyEvents(saga.events, rocksDBOperations)
                            .right()
                    }
                }
                is SagaStepA.Fold -> {
                    val res = saga.saga.failFast(rocksDBOperations, aggregateServiceFactory)
                    Either.right(res.fold({ t -> saga.ifError(t) }, { t -> saga.ifSuccess(t as List<EventDTO>)
                        .failFast(rocksDBOperations, aggregateServiceFactory) }))
                }
                is SagaStepA.And -> {
                    val resA = saga.a.failFast(rocksDBOperations, aggregateServiceFactory)
                    resA.flatMap { saga.b.failFast(rocksDBOperations, aggregateServiceFactory) }
                }
                is SagaStepA.ExitWithError -> {
                    Either.left(saga.error)
                }
                is SagaStepA.ReturnEvents -> {
                    Either.right(saga.events)
                }
            }
        }.fold({f -> f}, {CommandProcessingError.CommandProcessingFailed(Exception(it)).left()}) as EitherOf<CommandProcessingError, A>
    }
}

class LoggingInterpreter(private val log: Logger) : FunctionK<ForSagaStep, ForId> {
    @Suppress("UNCHECKED_CAST")
    override fun <A> invoke(fa: Kind<ForSagaStep, A>): IdOf<A> {
       when (val g = fa.fix()) {
            is SagaStepA.Fold -> {
                g.saga.log(log)
                log.info("  if success: ")
                g.ifSuccess(emptyList()).log(log)
                log.info("  if error: ")
                g.ifError(CommandProcessingError.CommandProcessingFailed(Exception("Some error"))).log(log)
            }
           is SagaStepA.ProcessCommand -> log.info("Process command ${g.commandDTO}")
           is SagaStepA.ApplyEvent -> log.info("Apply event ${g.event}")
           is SagaStepA.ApplyEvents -> log.info("Batch apply events ${g.events}")
           is SagaStepA.ExitWithError -> log.info("Exit with error.")
           is SagaStepA.ReturnEvents -> log.info("Return events.")
           is SagaStepA.And -> log.info("Return events.")
       }
        return Id.just(emptyList<EventDTO>()) as IdOf<A>
    }
}
