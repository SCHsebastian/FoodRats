package es.schsebastian.foodrats.app.navigation

/**
 * The external URL contract for the app. Hosted association files must match this exactly:
 *  - `https://foodrats.app/…`  → Android App Links (autoVerify) + iOS Universal Links
 *  - `foodrats://app/…`        → custom-scheme fallback
 *
 * The route discriminator is always the **first path segment**, so parsing is scheme- and
 * host-agnostic. That is why the custom scheme uses host `app`: `foodrats://app/meal/…` and
 * `https://foodrats.app/meal/…` both yield the path segments `[meal, …]`.
 */
object DeepLinks {
    const val WEB_SCHEME = "https"
    const val WEB_HOST = "foodrats.app"
    const val APP_SCHEME = "foodrats"
    const val APP_HOST = "app"

    const val SEGMENT_MEAL = "meal"   // /meal/{mealId}/{dayIso}  → Route.MealDetail
    const val SEGMENT_CREW = "crew"   // /crew/{crewId}           → Route.CrewSettings
}

/**
 * Pure URI → typed [Route] mapping. Scheme/host-agnostic; matches on path segments only. Returns
 * null for anything unrecognised or malformed — callers ignore a null (the link is a no-op rather
 * than a crash).
 *
 * Kept pure Kotlin on purpose (no `android.net.Uri` / `NavUri`): both platforms deliver the link
 * as a raw string, and a pure parser is testable on every target without a platform URI runtime.
 * Path segments are taken verbatim; the contract only uses URL-safe identifiers (Firestore doc ids,
 * ISO dates), so no percent-decoding is required.
 */
fun parseDeepLink(uriString: String): Route? {
    val segments = pathSegmentsOf(uriString)
    return when {
        segments.size >= 3 && segments[0] == DeepLinks.SEGMENT_MEAL ->
            Route.MealDetail(mealId = segments[1], dayIso = segments[2])

        segments.size >= 2 && segments[0] == DeepLinks.SEGMENT_CREW ->
            Route.CrewSettings(crewId = segments[1])

        else -> null
    }
}

/**
 * Extracts the path segments of a URI string, dropping scheme, authority, query and fragment.
 * `https://foodrats.app/meal/m1/d?x=1#y` and `foodrats://app/meal/m1/d` both yield `[meal, m1, d]`.
 */
private fun pathSegmentsOf(uriString: String): List<String> {
    val withoutQueryOrFragment = uriString.substringBefore('#').substringBefore('?')
    val schemeIdx = withoutQueryOrFragment.indexOf("://")
    val path = if (schemeIdx >= 0) {
        // Drop "scheme://authority", keeping everything from the first '/' of the path on.
        val afterAuthority = withoutQueryOrFragment.substring(schemeIdx + 3)
        val firstSlash = afterAuthority.indexOf('/')
        if (firstSlash >= 0) afterAuthority.substring(firstSlash) else ""
    } else {
        // No authority marker; treat the part after a bare "scheme:" (if present) as the path.
        val colon = withoutQueryOrFragment.indexOf(':')
        if (colon >= 0) withoutQueryOrFragment.substring(colon + 1) else withoutQueryOrFragment
    }
    return path.split('/').filter { it.isNotBlank() }
}
