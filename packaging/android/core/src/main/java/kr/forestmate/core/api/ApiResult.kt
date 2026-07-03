package kr.forestmate.core.api

sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()

    data class Failure(
        val statusCode: Int,
        val message: String,
        val cause: Throwable? = null,
    ) : ApiResult<Nothing>() {
        val isRetryable: Boolean get() = statusCode == 0 || statusCode == 408 || statusCode >= 500
        val displayMessage: String get() = if (statusCode > 0) "HTTP $statusCode $message" else message
    }

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}
