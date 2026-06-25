package es.schsebastian.foodrats.app.i18n

import es.schsebastian.foodrats.core.i18n.PluralStringKey
import foodrats.shared.generated.resources.Res
import foodrats.shared.generated.resources.recap_streak_subtitle
import foodrats.shared.generated.resources.recap_your_week_streak
import org.jetbrains.compose.resources.PluralStringResource

/**
 * Sibling of [SharedStringKey] for quantity-aware shared strings. Backed by `<plurals>`
 * resources and resolved via `resolvePlural`, so a 1-day streak reads "Racha de 1 día
 * publicando" instead of the grammatically wrong "Racha de 1 días publicando" — following
 * each locale's CLDR plural rules rather than a hardcoded plural noun.
 */
enum class SharedPluralKey(override val resourceId: PluralStringResource) : PluralStringKey {
    /** Weekly-recap streak scene caption — "%1$d-day posting streak". Takes %1$d = streak days. */
    RecapStreakSubtitle(Res.plurals.recap_streak_subtitle),

    /** "Your week" recap streak line — "%1$d-day streak". Takes %1$d = streak days. */
    RecapYourWeekStreak(Res.plurals.recap_your_week_streak),
}
