package de.fosstenbuch.utils

/**
 * Generic sealed class for representing operation results throughout the app.
 * Provides a consistent way to handle success, error, and loading states.
 *
 * Usage:
 * ```
 * fun fetchData(): Result<List<Trip>> {
 *     return try {
 *         Result.Success(repository.getTrips())
 *     } catch (e: Exception) {
 *         Result.Error(e)
 *     }
 * }
 * ```
 */
sealed class Result<out T> {
    /** Operation completed successfully with [data]. */
    data class Success<T>(val data: T) : Result<T>()

    /** Operation failed with an [exception] and optional user-facing [message]. */
    data class Error(
        val exception: Throwable? = null,
        val message: String = exception?.let { ErrorMapper.map(it) } ?: "Unbekannter Fehler"
    ) : Result<Nothing>()

    /** Operation is in progress. */
    data object Loading : Result<Nothing>()

    /** Whether this result is a success. */
    val isSuccess: Boolean get() = this is Success

    /** Whether this result is an error. */
    val isError: Boolean get() = this is Error

    /** Whether this result is loading. */
    val isLoading: Boolean get() = this is Loading

    /** Returns the data if this is a [Success], otherwise null. */
    fun getOrNull(): T? = (this as? Success)?.data

    /** Returns the data if this is a [Success], otherwise the [default] value. */
    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default

    /** Returns the error message if this is an [Error], otherwise null. */
    fun errorMessageOrNull(): String? = (this as? Error)?.message

    /**
     * Transforms the data of a [Success] result using [transform].
     * [Error] and [Loading] are passed through unchanged.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    /**
     * Transforms the data of a [Success] result using [transform] which itself returns a [Result].
     * [Error] and [Loading] are passed through unchanged.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> this
    }

    companion object {
        /** Creates a [Success] result with the given [data]. */
        fun <T> success(data: T): Result<T> = Success(data)

        /** Creates an [Error] result with the given [exception]. */
        fun error(exception: Throwable): Result<Nothing> = Error(exception)

        /** Creates an [Error] result with the given [message]. */
        fun error(message: String): Result<Nothing> = Error(message = message)

        /** Creates a [Loading] result. */
        fun loading(): Result<Nothing> = Loading
    }
}
