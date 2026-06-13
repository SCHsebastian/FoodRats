package es.schsebastian.foodrats.core.i18n

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun resolve(key: StringKey): String = stringResource(key.resourceId)

@Composable
fun resolve(key: StringKey, vararg args: Any): String = stringResource(key.resourceId, *args)

/**
 * Resolves a quantity-aware [PluralStringKey] for [quantity] using the active
 * locale's CLDR plural rules — the correct replacement for `if (n == 1) … else …`.
 * The count is forwarded as the sole format arg, so a `%1$d` placeholder renders it.
 */
@Composable
fun resolvePlural(key: PluralStringKey, quantity: Int): String =
    pluralStringResource(key.resourceId, quantity, quantity)

/**
 * Variant where the plural *form* is selected by [quantity] but the format args
 * are supplied explicitly — for the rare case where the substituted value differs
 * from the selecting count (e.g. an animated counter tweening toward the real total).
 */
@Composable
fun resolvePlural(key: PluralStringKey, quantity: Int, vararg args: Any): String =
    pluralStringResource(key.resourceId, quantity, *args)
