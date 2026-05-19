package es.schsebastian.foodrats.app.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.shared.generated.resources.Res
import foodrats.shared.generated.resources.nav_capture_cta
import foodrats.shared.generated.resources.nav_settings_cta
import org.jetbrains.compose.resources.StringResource

enum class SharedStringKey(override val resourceId: StringResource) : StringKey {
    NavCaptureCta(Res.string.nav_capture_cta),
    NavSettingsCta(Res.string.nav_settings_cta),
}
