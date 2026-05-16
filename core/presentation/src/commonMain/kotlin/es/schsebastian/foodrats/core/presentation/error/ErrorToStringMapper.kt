package es.schsebastian.foodrats.core.presentation.error

import es.schsebastian.foodrats.core.i18n.StringKey

fun interface ErrorToStringMapper<E> {
    fun map(error: E): StringKey
}
