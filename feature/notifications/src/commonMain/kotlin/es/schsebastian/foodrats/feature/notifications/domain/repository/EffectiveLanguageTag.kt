package es.schsebastian.foodrats.feature.notifications.domain.repository

/**
 * Resolves the device's CURRENT effective UI language as a bare BCP-47 tag ("en", "es", …).
 *
 * "Effective" = the explicit in-app language if the user picked one, otherwise the OS language
 * (`AppLocale.System`). Stored on the device-token doc so the server can localize the OS-rendered
 * `notification` block of a backgrounded push — which the client never sees and so cannot localize.
 *
 * Declared in the domain layer (a `fun interface` keeps test doubles trivial); the data layer wires
 * it to `LocalePort` + the platform `deviceLanguageTag()`.
 */
fun interface EffectiveLanguageTag {
    suspend operator fun invoke(): String
}
