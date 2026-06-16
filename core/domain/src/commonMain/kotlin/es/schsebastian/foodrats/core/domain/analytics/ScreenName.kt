package es.schsebastian.foodrats.core.domain.analytics

import kotlin.jvm.JvmInline

/**
 * Canonical analytics screen name (snake_case, ≤40 chars), derived from a navigation `Route`
 * *type's* simple name — never the raw route string (which carries args → high cardinality and
 * blows the GA4 length cap). Mapping lives at the navigation layer; this value object just makes the
 * wire string a distinct type so it can't be confused with an event name.
 */
@JvmInline
value class ScreenName(val wire: String)
