package de.fosstenbuch.domain.validation

/**
 * Represents the result of a validation operation.
 * Can contain multiple error messages for different fields.
 */
data class ValidationResult(
    val errors: Map<String, String> = emptyMap()
) {
    val isValid: Boolean get() = errors.isEmpty()

    fun errorFor(field: String): String? = errors[field]

    companion object {
        fun valid() = ValidationResult()

        fun invalid(vararg fieldErrors: Pair<String, String>) =
            ValidationResult(fieldErrors.toMap())
    }
}
