package es.schsebastian.foodrats.core.i18n

import org.jetbrains.compose.resources.PluralStringResource

/**
 * Sibling of [StringKey] for quantity-aware strings. A feature defines its own
 * `<Feature>PluralKey` enum implementing this and backs each entry with a
 * `<plurals>` resource; the UI resolves it via [resolvePlural], which delegates
 * to Compose Resources' CLDR-driven `pluralStringResource`. Use this instead of
 * hand-faking plurals with `if (count == 1) singular else plural` — that is only
 * correct for en/es and silently wrong for every other locale's plural rules.
 */
interface PluralStringKey {
    val resourceId: PluralStringResource
}
