package es.schsebastian.foodrats.core.domain.result

sealed interface Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>
    data class Err<out E>(val error: E) : Result<Nothing, E>

    companion object {
        fun <T> success(value: T): Result<T, Nothing> = Ok(value)
        fun <E> failure(error: E): Result<Nothing, E> = Err(error)
    }
}

inline fun <T, E, R> Result<T, E>.fold(onOk: (T) -> R, onErr: (E) -> R): R = when (this) {
    is Result.Ok  -> onOk(value)
    is Result.Err -> onErr(error)
}

inline fun <T, E, R> Result<T, E>.map(f: (T) -> R): Result<R, E> = when (this) {
    is Result.Ok  -> Result.Ok(f(value))
    is Result.Err -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(f: (T) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Ok  -> f(value)
    is Result.Err -> this
}

inline fun <T, E, E2> Result<T, E>.mapError(f: (E) -> E2): Result<T, E2> = when (this) {
    is Result.Ok  -> this
    is Result.Err -> Result.Err(f(error))
}

inline fun <T, E> Result<T, E>.getOrElse(default: (E) -> T): T = when (this) {
    is Result.Ok  -> value
    is Result.Err -> default(error)
}

fun <T, E> Result<T, E>.getOrNull(): T? = when (this) {
    is Result.Ok  -> value
    is Result.Err -> null
}
