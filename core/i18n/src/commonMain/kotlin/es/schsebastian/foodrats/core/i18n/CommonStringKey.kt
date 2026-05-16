package es.schsebastian.foodrats.core.i18n

import foodrats.core.i18n.generated.resources.Res
import foodrats.core.i18n.generated.resources.app_name
import foodrats.core.i18n.generated.resources.common_back
import foodrats.core.i18n.generated.resources.common_cancel
import foodrats.core.i18n.generated.resources.common_continue
import foodrats.core.i18n.generated.resources.common_open_settings
import foodrats.core.i18n.generated.resources.common_retry
import org.jetbrains.compose.resources.StringResource

enum class CommonStringKey(override val resourceId: StringResource) : StringKey {
    AppName(Res.string.app_name),
    Retry(Res.string.common_retry),
    Cancel(Res.string.common_cancel),
    Continue(Res.string.common_continue),
    Back(Res.string.common_back),
    OpenSettings(Res.string.common_open_settings),
}
