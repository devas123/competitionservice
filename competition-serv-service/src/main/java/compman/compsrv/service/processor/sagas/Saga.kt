package compman.compsrv.service.processor.sagas

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.either.applicativeError.catch
import arrow.core.extensions.either.monad.monad
import arrow.core.extensions.id.monad.monad
import arrow.free.Free
import arrow.free.foldMap
import arrow.higherkind
import arrow.mtl.StateT
import arrow.mtl.StateTPartialOf
import arrow.mtl.extensions.statet.monad.monad
import arrow.mtl.fix
import arrow.mtl.run
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.RocksDBOperations
import compman.compsrv.service.processor.command.AggregateServiceFactory
import compman.compsrv.service.processor.command.AggregatesWithEvents
import compman.compsrv.service.processor.command.executeInAppropriateService
import org.slf4j.Logger

@higherkind
sealed class SagaStepA<out A> : SagaStepAOf<A> {
    data class ProcessCommand(val commandDTO: CommandDTO) : SagaStepA<AggregatesWithEvents<AbstractAggregate>>()
    data class ApplyEvent(val aggregate: Either<Unit, AbstractAggregate>, val event: EventDTO) :
        SagaStepA<Either<Unit, AbstractAggregate>>()

    data class ApplyEvents(val aggregate: Either<Unit, AbstractAggregate>, val events: List<EventDTO>) :
        SagaStepA<Either<Unit, AbstractAggregate>>()

    data class CommandAndThen<A>(
        val saga: SagaStep<AggregatesWithEvents<AbstractAggregate>>,
        val next: (AggregatesWithEvents<AbstractAggregate>) -> SagaStep<A>,
        val ifError: (AggregatesWithEvents<AbstractAggregate>, SagaExecutionError) -> SagaStep<A>
    ) : SagaStepA<A>()

    data class EventAndThen<A>(
        val saga: SagaStep<Either<Unit, AbstractAggregate>>,
        val next: (Either<Unit, AbstractAggregate>) -> SagaStep<A>,
        val ifError: (Either<Unit, AbstractAggregate>, SagaExecutionError) -> SagaStep<A>
    ) : SagaStepA<A>()

    data class And<A>(val a: SagaStep<A>, val b: SagaStep<A>) : SagaStepA<A>()
    data class ExitWithError(val error: SagaExecutionError) : SagaStepA<SagaExecutionError>()
    data class ReturnEvents(val events: AggregatesWithEvents<AbstractAggregate>) : SagaStepA<AggregatesWithEvents<AbstractAggregate>>()
}

class ForSagaStep private constructor() {
    companion object
}


typealias SagaStepAOf<A> = Kind<ForSagaStep, A>

@Suppress("NOTHING_TO_INLINE")
inline fun <A> SagaStepAOf<A>.fix(): SagaStepA<A> =
    this as SagaStepA<A>

typealias SagaStep<A> = Free<ForSagaStep, A>

fun processCommand(commandDTO: CommandDTO): SagaStep<AggregatesWithEvents<AbstractAggregate>> =
    Free.liftF(SagaStepA.ProcessCommand(commandDTO))

fun applyEvent(aggregate: Either<Unit, AbstractAggregate>, event: EventDTO): SagaStep<Either<Unit, AbstractAggregate>> =
    Free.liftF(SagaStepA.ApplyEvent(aggregate, event))

fun applyEvents(
    aggregate: Either<Unit, AbstractAggregate>,
    events: List<EventDTO>
): SagaStep<Either<Unit, AbstractAggregate>> = Free.liftF(SagaStepA.ApplyEvents(aggregate, events))

fun error(err: SagaExecutionError): SagaStep<SagaExecutionError> = Free.liftF(SagaStepA.ExitWithError(err))
fun returnEvents(events: AggregatesWithEvents<AbstractAggregate>): SagaStep<AggregatesWithEvents<AbstractAggregate>> = Free.liftF(SagaStepA.ReturnEvents(events))
inline fun <reified A> and(a: SagaStep<A>, b: SagaStep<A>): SagaStep<A> = Free.liftF(SagaStepA.And(a, b))
inline fun <reified A> commandAndThen(
    saga: SagaStep<AggregatesWithEvents<AbstractAggregate>>,
    noinline next: (AggregatesWithEvents<AbstractAggregate>) -> SagaStep<A>,
    noinline ifError: (AggregatesWithEvents<AbstractAggregate>, SagaExecutionError) -> SagaStep<A>
): SagaStep<A> = Free.liftF(SagaStepA.CommandAndThen(saga, next, ifError))

inline fun <reified A> andThenForEvent(
    saga: SagaStep<Either<Unit, AbstractAggregate>>,
    noinline next: (Either<Unit, AbstractAggregate>) -> SagaStep<A>,
    noinline ifError: (Either<Unit, AbstractAggregate>, SagaExecutionError) -> SagaStep<A>
): SagaStep<A> = Free.liftF(SagaStepA.EventAndThen(saga, next, ifError))

inline fun <reified A> SagaStep<AggregatesWithEvents<AbstractAggregate>>.andThen(
    noinline ifSuccess: (AggregatesWithEvents<AbstractAggregate>) -> SagaStep<A>,
    noinline ifError: (AggregatesWithEvents<AbstractAggregate>, SagaExecutionError) -> SagaStep<A>
) = commandAndThen(this, ifSuccess, ifError)


fun SagaStep<AggregatesWithEvents<AbstractAggregate>>.execute() = this.andThen({ list ->
    if (list.isEmpty()) {
        applyEvents(Unit.left(), emptyList())
    } else {
        list.map { applyEvents(it.first.right(), it.second) }
            .reduce { acc, free -> and(acc, free) }
    }
}, { _, SagaExecutionError -> error(SagaExecutionError) })

inline fun <reified A> SagaStep<Either<Unit, AbstractAggregate>>.eventAndThen(
    noinline next: (Either<Unit, AbstractAggregate>) -> SagaStep<A>,
    noinline ifError: (Either<Unit, AbstractAggregate>, SagaExecutionError) -> SagaStep<A>
) =
    andThenForEvent(this, next, ifError)

fun sagaInterpreterEither(rocksDBOperations: RocksDBOperations, aggregateServiceFactory: AggregateServiceFactory) =
    SagaExecutionFailFast(rocksDBOperations, aggregateServiceFactory)

fun <A> SagaStep<A>.failFast(
    rocksDBOperations: RocksDBOperations,
    aggregateServiceFactory: AggregateServiceFactory
): Either<SagaExecutionError, A> {
    return foldMap(sagaInterpreterEither(rocksDBOperations, aggregateServiceFactory), Either.monad()).fix()
}

fun <A> SagaStep<A>.accumulate(
    rocksDBOperations: RocksDBOperations,
    aggregateServiceFactory: AggregateServiceFactory
): StateT<Either<SagaExecutionError, AggregatesWithEvents<AbstractAggregate>>, Kind<ForEither, SagaExecutionError>, A> {
    return foldMap(
        SagaExecutionAccumulateEvents(rocksDBOperations, aggregateServiceFactory),
        StateT.monad<Either<SagaExecutionError, AggregatesWithEvents<AbstractAggregate>>, EitherPartialOf<SagaExecutionError>>(
            Either.monad()
        )
    ).fix()
}

fun <A> StateT<Either<SagaExecutionError, AggregatesWithEvents<AbstractAggregate>>, Kind<ForEither, SagaExecutionError>, A>.doRun() = this.run(emptyList<Pair<AbstractAggregate, List<EventDTO>>>().right()).fix().flatMap { it.a }

fun <A> SagaStep<A>.log(log: Logger, level: Int = 0): Id<A> {
    return foldMap(LoggingInterpreter(log, level), Id.monad()).fix()
}

fun <R> eCatch(block: () -> R): Either<SagaExecutionError, R> = catch({ SagaExecutionError.EventApplicationFailed(it) }, block)
fun <R> cCatch(block: () -> R): Either<SagaExecutionError, R> = catch({ SagaExecutionError.CommandProcessingFailed(it) }, block)

@Suppress("UNCHECKED_CAST")
class SagaExecutionAccumulateEvents(
    private val rocksDBOperations: RocksDBOperations,
    private val aggregateServiceFactory: AggregateServiceFactory
) : FunctionK<ForSagaStep, StateTPartialOf<Either<SagaExecutionError, AggregatesWithEvents<AbstractAggregate>>, EitherPartialOf<SagaExecutionError>>> {
    override fun <A> invoke(fa: SagaStepAOf<A>): Kind<StateTPartialOf<Either<SagaExecutionError, AggregatesWithEvents<AbstractAggregate>>, EitherPartialOf<SagaExecutionError>>, A> {
        return when (val saga = fa.fix()) {
            is SagaStepA.ProcessCommand -> {
                StateT { either ->
                    val aggregates = cCatch { executeInAppropriateService(
                        saga.commandDTO,
                        rocksDBOperations,
                        aggregateServiceFactory
                    ) }
                    either.flatMap { list -> aggregates.map { agg -> (list + agg).right() toT (agg as A) } }
                }
            }
            is SagaStepA.ApplyEvent -> {
                StateT { either ->
                    val aggregate = eCatch {
                        val a = aggregateServiceFactory.getAggregateService(saga.event).getAggregate(saga.event, rocksDBOperations)
                        a.applyEvent(saga.event, rocksDBOperations)
                        a
                    }
                    either.flatMap { list ->
                        aggregate.map { agg ->
                            (list + (agg to listOf(saga.event))).right() toT (listOf(
                                agg to listOf(
                                    saga.event
                                )
                            ) as A)
                        }
                    }
                }
//                StateT { list -> ((list + (aggregate to listOf(saga.event))) toT (listOf(aggregate to listOf(saga.event)) as A)).right() }
            }
            is SagaStepA.ApplyEvents -> {
                if (saga.events.isEmpty()) {
                    StateT { (SagaExecutionError.GenericError("Events list is empty")).left() }
                } else {
                    StateT { either ->
                        val agg = eCatch {
                            val a = saga.aggregate.fold({
                                aggregateServiceFactory.getAggregateService(saga.events.first())
                                    .getAggregate(saga.events.first(), rocksDBOperations)
                            }, { it })
                            a.applyEvents(saga.events, rocksDBOperations)
                            a
                        }
                        either.flatMap { list ->
                            agg.map { aggregate ->
                                val accumulated = list + (aggregate to saga.events)
                                accumulated.right() toT (listOf(agg to saga.events) as A)
                            }
                        }
                    }
                }
            }
            is SagaStepA.CommandAndThen -> {
                StateT { either ->
                    val res = saga.saga.accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
                        .flatMap { list: AggregatesWithEvents<AbstractAggregate> ->
                            val m = saga.next(list).accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
                            m.handleErrorWith {
                                saga.ifError(list, it).accumulate(rocksDBOperations, aggregateServiceFactory)
                                    .doRun()
                            }
                        }
                    either.flatMap { list -> res.map {
                        list1 -> (list + list1).right() toT (list1 as A)  } }}
            }

            is SagaStepA.And -> {
                StateT { either ->
                    val resA = saga.a.accumulate(rocksDBOperations, aggregateServiceFactory)
                    val t = resA.flatMap(Either.monad()) { saga.b.accumulate(rocksDBOperations, aggregateServiceFactory) }
                    either.flatMap { list -> t.doRun().map { list1 ->
                    val accumulated = list + list1
                    (accumulated).right() toT (accumulated as A)  } }
                }

            }
            is SagaStepA.ExitWithError -> {
                StateT { saga.error.left() }
            }

            is SagaStepA.ReturnEvents -> {
                StateT { it.map { list ->
                    val collected = list + saga.events
                    collected.right() toT (collected as A)
                } }
            }
            is SagaStepA.EventAndThen -> {
                StateT { either ->
                    val res = saga.saga.accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
                        .flatMap { list: AggregatesWithEvents<AbstractAggregate> ->
                            val agg = Either.fromNullable(list.firstOrNull()?.first)
                            val m = saga.next(agg).accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
                            m.handleErrorWith { saga.ifError(agg, it).accumulate(rocksDBOperations, aggregateServiceFactory).doRun() }
                        }
                    either.flatMap { list -> res.map {
                            list1 -> (list + list1).right() toT (list1 as A)  } }}
            }
        }

//        return kotlin.runCatching {
//        }.fold({ f -> f },
//            {
//                SagaExecutionError.CommandProcessingFailed(Exception(it)).left()
//            })
    }
}


@Suppress("UNCHECKED_CAST")
class SagaExecutionFailFast(
    private val rocksDBOperations: RocksDBOperations,
    private val aggregateServiceFactory: AggregateServiceFactory
) : FunctionK<ForSagaStep, EitherPartialOf<SagaExecutionError>> {
    override fun <A> invoke(fa: SagaStepAOf<A>): EitherOf<SagaExecutionError, A> {
        return kotlin.runCatching {
            when (val saga = fa.fix()) {
                is SagaStepA.ProcessCommand -> {
                    Either.right(
                        executeInAppropriateService(
                            saga.commandDTO,
                            rocksDBOperations,
                            aggregateServiceFactory
                        )
                    )
                }
                is SagaStepA.ApplyEvent -> {
                    aggregateServiceFactory.getAggregateService(saga.event).getAggregate(saga.event, rocksDBOperations)
                        .applyEvent(saga.event, rocksDBOperations).right()
                }
                is SagaStepA.ApplyEvents -> {
                    if (saga.events.isEmpty()) {
                        Either.left(SagaExecutionError.GenericError("No events to apply."))
                    } else {
                        val agg = aggregateServiceFactory.getAggregateService(saga.events[0])
                        agg.getAggregate(saga.events[0], rocksDBOperations).applyEvents(saga.events, rocksDBOperations)
                            .right()
                    }
                }
                is SagaStepA.CommandAndThen -> {
                    val res = saga.saga.failFast(rocksDBOperations, aggregateServiceFactory)
                    res.flatMap { t ->
                        saga.next(t)
                            .failFast(rocksDBOperations, aggregateServiceFactory)
                            .fold({ err ->
                                saga.ifError(t, err).failFast(rocksDBOperations, aggregateServiceFactory)
                                Either.left(err)
                            }, { Either.right(it) })
                    }
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
                is SagaStepA.EventAndThen -> {
                    val exec = saga.saga.failFast(rocksDBOperations, aggregateServiceFactory)
                    exec.flatMap { t ->
                        saga.next(t).failFast(rocksDBOperations, aggregateServiceFactory)
                            .fold({ err ->
                                saga.ifError(t, err).failFast(rocksDBOperations, aggregateServiceFactory)
                                Either.left(err)
                            }, { Either.right(it) })
                    }
                }
            }
        }.fold({ f -> f },
            {
                SagaExecutionError.CommandProcessingFailed(Exception(it)).left()
            }) as EitherOf<SagaExecutionError, A>
    }
}

class LoggingInterpreter(private val log: Logger, private val level: Int = 0) : FunctionK<ForSagaStep, ForId> {
    private fun info(text: String) {
        log.info("${".".repeat(level * 2)}$text")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A> invoke(fa: Kind<ForSagaStep, A>): IdOf<A> {
        when (val g = fa.fix()) {
            is SagaStepA.CommandAndThen -> {
                g.saga.log(log)
                info("And then {")
                g.next(emptyList()).log(log, level + 1)
                info("} handle error: {")
                g.ifError(emptyList(), SagaExecutionError.GenericError("Some error")).log(log, level + 1)
                info("}")
            }
            is SagaStepA.ProcessCommand -> info("Process command ${g.commandDTO}")
            is SagaStepA.ApplyEvent -> info("Apply event ${g.event}")
            is SagaStepA.ApplyEvents -> info("Batch apply events ${g.events}")
            is SagaStepA.ExitWithError -> info("Exit with error.")
            is SagaStepA.ReturnEvents -> info("Return events.")
            is SagaStepA.And -> info("Return events.")
            is SagaStepA.EventAndThen -> {
                g.saga.log(log)
                info("And then if success: {")
                g.next(Unit.left()).log(log, level + 1)
                info("} handle error: {")
                g.ifError(Unit.left(), SagaExecutionError.GenericError("Some error")).log(log, level + 1)
                info("}")
            }
        }
        return Id.just(emptyList<EventDTO>()) as IdOf<A>
    }
}
