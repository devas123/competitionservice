package compman.compsrv.util

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.either.applicativeError.applicativeError
import arrow.core.extensions.either.applicativeError.raiseError
import arrow.core.extensions.nonemptylist.semigroup.semigroup
import arrow.core.extensions.validated.applicativeError.applicativeError
import arrow.typeclasses.ApplicativeError
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import compman.compsrv.model.dto.competition.CategoryRestrictionType

sealed class ValidationError(val msg: String) {
    data class DoesNotContain(val value: String) : ValidationError("Did not contain $value")
    data class MaxLength(val value: Int) : ValidationError("Exceeded length of $value")
    data class NotNumerical(val value: String, val type: CategoryRestrictionType) : ValidationError("Value $value in the restriction of type $type is not numerical")
    data class NotValid(val value: Any, val reasons: Nel<ValidationError>): ValidationError("$value is not valid: ${reasons.toList().joinToString(", ")}")
}


sealed class Rules<F>(A: ApplicativeError<F, Nel<ValidationError>>) : ApplicativeError<F, Nel<ValidationError>> by A {

    private fun isNumericOrNull(value: String?, type: CategoryRestrictionType): Kind<F, String?> {
        return if (value.isNullOrBlank()) {
            just(value)
        } else {
            runCatching { value.toBigDecimal() }.map { just(value) }.getOrElse { raiseError(ValidationError.NotNumerical(value, type).nel()) }
        }
    }


    private fun CategoryRestrictionDTO.validateMaxValue(): Kind<F, CategoryRestrictionDTO> =
            isNumericOrNull(maxValue, type).map { this }



    private fun CategoryRestrictionDTO.validateMinValue(): Kind<F, CategoryRestrictionDTO> =
            isNumericOrNull(minValue, type).map { this }


    private fun CategoryRestrictionDTO.nameMaxLength(maxLength: Int): Kind<F, CategoryRestrictionDTO> =
            if (name != null && name.length <= maxLength) just(this)
            else raiseError(ValidationError.MaxLength(maxLength).nel())

    fun CategoryRestrictionDTO.validate(): Kind<F, CategoryRestrictionDTO> =
            map(validateMaxValue(), validateMinValue(), nameMaxLength(50)) {
                this
            }.handleErrorWith { raiseError(ValidationError.NotValid(this, it).nel()) }

    object ErrorAccumulationStrategy :
            Rules<ValidatedPartialOf<Nel<ValidationError>>>(Validated.applicativeError(NonEmptyList.semigroup()))

    object FailFastStrategy :
            Rules<EitherPartialOf<Nel<ValidationError>>>(Either.applicativeError())

    companion object {
        infix fun <A> failFast(f: FailFastStrategy.() -> A): A = f(FailFastStrategy)
        infix fun <A> accumulateErrors(f: ErrorAccumulationStrategy.() -> A): A = f(ErrorAccumulationStrategy)
    }
}