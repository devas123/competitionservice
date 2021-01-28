package compman.compsrv.service.processor.saga

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.either.applicativeError.catch
import arrow.core.extensions.either.monad.monad
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
import compman.compsrv.errors.getCompensatingActions
import compman.compsrv.errors.getEvents
import compman.compsrv.errors.getOrigin
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AggregateServiceFactory

@higherkind
sealed class SagaStepA<out A> : SagaStepAOf<A> {
    data class ApplyEvent(
        val aggregate: Either<Unit, AbstractAggregate>,
        val event: EventDTO) : SagaStepA<List<EventDTO>>()

    data class And<A>(val a: SagaStep<A>, val b: SagaStep<A>, val compensateIfBFails: EventDTO?) : SagaStepA<A>()
}

class ForSagaStep private constructor() {
    companion object
}


typealias SagaStepAOf<A> = Kind<ForSagaStep, A>

@Suppress("NOTHING_TO_INLINE")
inline fun <A> SagaStepAOf<A>.fix(): SagaStepA<A> =
    this as SagaStepA<A>

typealias SagaStep<A> = Free<ForSagaStep, A>

fun applyEvent(
    aggregate: Either<Unit, AbstractAggregate>,
    event: EventDTO
): SagaStep<List<EventDTO>> =
    Free.liftF(SagaStepA.ApplyEvent(aggregate, event))


fun <A> and(a: SagaStep<A>, b: SagaStep<A>, compensateIfBFails: EventDTO?): SagaStep<A> = Free.liftF(SagaStepA.And(a, b, compensateIfBFails))

fun <A> SagaStep<A>.andStep(b: SagaStep<A>, compensateIfBFails: EventDTO?) = and(this, b, compensateIfBFails)

fun <A> SagaStep<A>.accumulate(
    rocksDBOperations: DBOperations,
    aggregateServiceFactory: AggregateServiceFactory
): StateT<Either<SagaExecutionError, List<EventDTO>>, Kind<ForEither, SagaExecutionError>, A> {
    return foldMap(
        SagaExecutionAccumulateEvents(rocksDBOperations, aggregateServiceFactory),
        StateT.monad<Either<SagaExecutionError, List<EventDTO>>, EitherPartialOf<SagaExecutionError>>(
            Either.monad()
        )
    ).fix()
}

fun <A> StateT<Either<SagaExecutionError, List<EventDTO>>, Kind<ForEither, SagaExecutionError>, A>.doRun() =
    this.run(emptyList<EventDTO>().right()).fix().flatMap { it.a }


fun <R> eCatch(block: () -> R): Either<SagaExecutionError, R> =
    catch({ SagaExecutionError.EventApplicationFailed(it) }, block)

@Suppress("UNCHECKED_CAST")
class SagaExecutionAccumulateEvents(
    private val rocksDBOperations: DBOperations,
    private val aggregateServiceFactory: AggregateServiceFactory
) : FunctionK<ForSagaStep, StateTPartialOf<Either<SagaExecutionError, List<EventDTO>>, EitherPartialOf<SagaExecutionError>>> {
    override fun <A> invoke(fa: SagaStepAOf<A>): Kind<StateTPartialOf<Either<SagaExecutionError, List<EventDTO>>, EitherPartialOf<SagaExecutionError>>, A> {
        return when (val saga = fa.fix()) {
            is SagaStepA.ApplyEvent -> {
                StateT { either ->
                    val aggregate = eCatch {
                        val a = saga.aggregate.fold({
                            aggregateServiceFactory.getAggregateService(saga.event)
                                .getAggregate(saga.event, rocksDBOperations)
                        }, { it })
                        aggregateServiceFactory.applyEvent(a, saga.event, rocksDBOperations)
                    }
                    val k = either.flatMap { list ->
                        aggregate.map {
                            val a = list + saga.event
                            a.right() toT (listOf(saga.event) as A)
                        }
                    }
                    k
                }
            }
            is SagaStepA.And -> {
                StateT { either ->
                    either.flatMap { events ->
                        val resA = saga.a.accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
                        resA.flatMap { alist ->
                            saga.b.accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
                                .mapLeft { e ->
                                    saga.compensateIfBFails?.let {
                                        applyEvent(Unit.left(), saga.compensateIfBFails).accumulate(
                                            rocksDBOperations,
                                            aggregateServiceFactory
                                        ).doRun()
                                            .fold({ error ->
                                                SagaExecutionError.CompositeError(listOf(error, e))
                                            }, {
                                                SagaExecutionError.ErrorWithCompensatingActions(
                                                    e.getOrigin(),
                                                    e.getEvents() + events,
                                                    e.getCompensatingActions() + saga.compensateIfBFails
                                                )
                                            })
                                    } ?: e
                                }
                                .map { list ->
                                    val a = events + alist + list
                                    a.right() toT ((alist + list) as A)
                                }
                        }
                    }
                }
            }
        }

//        return kotlin.runCatching {
//        }.fold({ f -> f },
//            {
//                SagaExecutionError.CommandProcessingFailed(Exception(it)).left()
//            })
    }
}