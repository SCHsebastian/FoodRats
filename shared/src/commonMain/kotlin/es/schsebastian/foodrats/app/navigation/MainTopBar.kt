package es.schsebastian.foodrats.app.navigation

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.i18n.resolve

/**
 * App-specific top bar for the Main scaffold: a transparent center app bar (matching the mock's
 * `FrTopBar`) with the profile avatar (leading), the active tab title, and an optional
 * crew-settings action. Navigation chrome, co-located with [MainBottomBar] — not a design-system
 * component, so it resolves its own [SharedStringKey] labels directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTopBar(
    isStats: Boolean,
    avatarInitials: String,
    avatarUrl: String?,
    showSettings: Boolean,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            FrText(
                text = resolve(if (isStats) SharedStringKey.NavTabStats else SharedStringKey.NavTabFeed),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(onClick = onProfileClick) {
                FrAvatar(
                    initials = avatarInitials,
                    imageUrl = avatarUrl,
                    size = Sizes.avatarSm,
                    contentDescription = resolve(SharedStringKey.NavProfileCta),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        actions = {
            if (showSettings) {
                FrIconButton(
                    icon = FrIcons.Settings,
                    onClick = onSettingsClick,
                    contentDescription = resolve(SharedStringKey.NavSettingsCta),
                )
            }
        },
    )
}
