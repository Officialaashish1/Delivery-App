package `in`.localrush.core.network.util

import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.ErrorEnvelopeParser
import `in`.localrush.core.common.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Wrap every Retrofit suspend call: `apiCall { api.me() }` → `Result<User, AppError>`.
 * Repositories must never let exceptions escape.
 */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): Result<T, AppError> = try {
    Result.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.Err(t.toAppError())
}

/** Variant for endpoints that return `Response<Unit>` / 204. */
suspend inline fun apiCallUnit(crossinline block: suspend () -> Response<Unit>): Result<Unit, AppError> = try {
    val r = block()
    if (r.isSuccessful) Result.Ok(Unit) else Result.Err(ErrorEnvelopeParser.parse(r.code(), r.errorBody()?.string()))
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.Err(t.toAppError())
}

fun Throwable.toAppError(): AppError = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val parsed = ErrorEnvelopeParser.parse(code(), body)
        if (code() == 401 && parsed is AppError.Api && parsed.forcesLogout) AppError.Unauthenticated(parsed.message) else parsed
    }
    is SocketTimeoutException -> AppError.Timeout()
    is UnknownHostException -> AppError.Network()
    is IOException -> AppError.Network(message ?: "Network error")
    is SerializationException -> AppError.Unknown("Unexpected response from server", this)
    else -> AppError.Unknown(message ?: "Something went wrong", this)
}
