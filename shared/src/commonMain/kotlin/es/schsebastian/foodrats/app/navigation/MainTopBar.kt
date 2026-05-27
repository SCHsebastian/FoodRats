package es.schsebastian.foodrats.app.navigation

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.i18n.resolve

/**
 * App-specific top bar for the Main scaffold: a brand-tinted center app bar with the profile avatar
 * (leading), the active tab title, and an optional crew-settings action. Navigation chrome,
 * co-located with [MainBottomBar] — not a design-system component, so it resolves its own
 * [SharedStringKey] labels directly.
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
        title = { Text(resolve(if (isStats) SharedStringKey.NavTabStats else SharedStringKey.NavTabFeed)) },
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
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        actions = {
            if (showSettings) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = FrIcons.Settings,
                        contentDescription = resolve(SharedStringKey.NavSettingsCta),
                    )
                }
            }
        },
    )
}
