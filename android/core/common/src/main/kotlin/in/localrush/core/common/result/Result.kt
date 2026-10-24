package `in`.localrush.core.common.result

/**
 * Typed result used by every repository in LocalRush: `Result<T, AppError>`.
 * Never throw from a repository; map exceptions to [AppError] and return [Err].
 */
sealed class Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>()
    data class Err<out E>(val error: E) : Result<Nothing, E>()

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): E? = (this as? Err)?.error

    inline fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    inline fun <R> mapError(transform: (E) -> R): Result<T, R> = when (this) {
        is Ok -> this
        is Err -> Err(transform(error))
    }

    inline fun onSuccess(block: (T) -> Unit): Result<T, E> {
        if (this is Ok) block(value)
        return this
    }

    inline fun onFailure(block: (E) -> Unit): Result<T, E> {
        if (this is Err) block(error)
        return this
    }

    inline fun <R> fold(onOk: (T) -> R, onErr: (E) -> R): R = when (this) {
        is Ok -> onOk(value)
        is Err -> onErr(error)
    }

    companion object {
        fun <T> ok(value: T): Result<T, Nothing> = Ok(value)
        fun <E> err(error: E): Result<Nothing, E> = Err(error)
    }
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Ok -> transform(value)
    is Result.Err -> this
}

fun <T, E> Result<T, E>.getOrDefault(default: T): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> default
}

inline fun <T, E> Result<T, E>.getOrElse(block: (E) -> T): T = when (this) {
    is Result.Ok -> value
    is Result.Err -> block(error)
}

typealias AppResult<T> = Result<T, AppError>
typealias EmptyResult = Result<Unit, AppError>
