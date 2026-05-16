package es.schsebastian.foodrats.core.domain.model

import es.schsebastian.foodrats.core.domain.result.Result

value class AccountId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Result<AccountId, IdError> {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) Result.failure(IdError.Blank)
            else Result.success(AccountId(trimmed))
        }
    }
}
