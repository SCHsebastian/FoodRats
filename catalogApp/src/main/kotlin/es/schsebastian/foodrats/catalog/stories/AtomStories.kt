package es.schsebastian.foodrats.catalog.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.catalog.components.CatalogScene
import es.schsebastian.foodrats.catalog.components.CatalogSectionHeader
import es.schsebastian.foodrats.catalog.registry.CatalogEntry
import es.schsebastian.foodrats.catalog.registry.CatalogGroup
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrChip
import es.schsebastian.foodrats.core.designsystem.atoms.FrDivider
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrShutterButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrSpacer
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

internal fun atomStories(): List<CatalogEntry> = listOf(
    CatalogEntry("atom.avatar",        CatalogGroup.ATOMS, "FrAvatar",        "Initial-circle avatar at sm/md/lg sizes") { AvatarStory() },
    CatalogEntry("atom.button",        CatalogGroup.ATOMS, "FrButton",        "Primary / Secondary / Ghost · enabled & disabled") { ButtonStory() },
    CatalogEntry("atom.chip",          CatalogGroup.ATOMS, "FrChip",          "Selectable AssistChip with selected / disabled states") { ChipStory() },
    CatalogEntry("atom.divider",       CatalogGroup.ATOMS, "FrDivider",       "Thickness & tint variants") { DividerStory() },
    CatalogEntry("atom.icon",          CatalogGroup.ATOMS, "FrIcon",          "Tintable icon at three sizes") { IconStory() },
    CatalogEntry("atom.icons",         CatalogGroup.ATOMS, "FrIcons",         "Project-wide ImageVector catalog (core-only set)") { IconsCatalogStory() },
    CatalogEntry("atom.iconbutton",    CatalogGroup.ATOMS, "FrIconButton",    "Icon-only button — back, settings, chevrons") { IconButtonStory() },
    CatalogEntry("atom.progress",      CatalogGroup.ATOMS, "FrProgressIndicator", "Default + tinted spinners") { ProgressStory() },
    CatalogEntry("atom.shutter",       CatalogGroup.ATOMS, "FrShutterButton", "Capture-screen shutter — enabled & disabled") { ShutterStory() },
    CatalogEntry("atom.spacer",        CatalogGroup.ATOMS, "FrSpacer",        "Spacing token visualization") { SpacerStory() },
    CatalogEntry("atom.text",          CatalogGroup.ATOMS, "FrText",          "Wraps Material Text with FrTextStyles") { TextStory() },
    CatalogEntry("atom.textfield",     CatalogGroup.ATOMS, "FrTextField",     "OutlinedTextField with error / disabled states") { TextFieldStory() },
)

@Composable
private fun AvatarStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Sizes — Sm / Md / Lg") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrAvatar(initials = "sc", size = Sizes.avatarSm)
                FrAvatar(initials = "an", size = Sizes.avatarMd)
                FrAvatar(initials = "rk", size = Sizes.avatarLg)
            }
        }
        CatalogScene(label = "Truncation — first two letters") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrAvatar(initials = "sebastian")
                FrAvatar(initials = "x")
                FrAvatar(initials = "1234")
            }
        }
    }
}

@Composable
private fun ButtonStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Variants — enabled") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FrButton(label = "Primary",   onClick = {})
                FrButton(label = "Secondary", onClick = {}, variant = FrButtonVariant.Secondary)
                FrButton(label = "Ghost",     onClick = {}, variant = FrButtonVariant.Ghost)
            }
        }
        CatalogScene(label = "Variants — disabled") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FrButton(label = "Primary",   onClick = {}, enabled = false)
                FrButton(label = "Secondary", onClick = {}, variant = FrButtonVariant.Secondary, enabled = false)
                FrButton(label = "Ghost",     onClick = {}, variant = FrButtonVariant.Ghost,     enabled = false)
            }
        }
    }
}

@Composable
private fun ChipStory() {
    var pick by remember { mutableStateOf("Salad") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Selection — single-select group") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("Pizza", "Salad", "Pasta", "Vegan").forEach { tag ->
                    FrChip(label = tag, onClick = { pick = tag }, selected = pick == tag)
                }
            }
        }
        CatalogScene(label = "Disabled") {
            FrChip(label = "Off", onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun DividerStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Default") { FrDivider() }
        CatalogScene(label = "Thick") { FrDivider(thickness = 3.dp) }
        CatalogScene(label = "Tinted (primary)") {
            FrDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
        }
    }
}

@Composable
private fun IconStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Sizes — Sm / Md / Lg") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrIcon(image = FrIcons.Home, modifier = Modifier.size(Sizes.iconSm), contentDescription = "Home")
                FrIcon(image = FrIcons.Home, modifier = Modifier.size(Sizes.iconMd), contentDescription = null)
                FrIcon(image = FrIcons.Home, modifier = Modifier.size(Sizes.iconLg), contentDescription = null)
            }
        }
        CatalogScene(label = "Tinted") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrIcon(
                    image = FrIcons.Settings,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconMd),
                )
                FrIcon(
                    image = FrIcons.Settings,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(Sizes.iconMd),
                )
            }
        }
    }
}

@Composable
private fun IconsCatalogStory() {
    val entries = listOf(
        "Back"          to FrIcons.Back,
        "Camera*"       to FrIcons.Camera,
        "AddPhoto*"     to FrIcons.AddPhoto,
        "GalleryImport*" to FrIcons.GalleryImport,
        "CameraOff*"    to FrIcons.CameraOff,
        "Settings"      to FrIcons.Settings,
        "Home"          to FrIcons.Home,
        "Stats*"        to FrIcons.Stats,
        "ChevronLeft"   to FrIcons.ChevronLeft,
        "ChevronRight"  to FrIcons.ChevronRight,
    )
    CatalogScene(
        label = "FrIcons",
        description = "* = material-icons-extended substitute (no KMP iOS artifact).",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            entries.chunked(4).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    rowItems.forEach { (label, icon) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.size(64.dp),
                        ) {
                            FrIcon(image = icon, modifier = Modifier.size(Sizes.iconMd), contentDescription = label)
                            FrSpacer(size = Spacing.xxs)
                            FrText(text = label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconButtonStory() {
    CatalogScene(label = "Common controls") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FrIconButton(icon = FrIcons.Back, onClick = {}, contentDescription = "Back")
            FrIconButton(icon = FrIcons.Settings, onClick = {}, contentDescription = "Settings")
            FrIconButton(icon = FrIcons.ChevronLeft, onClick = {}, contentDescription = "Prev")
            FrIconButton(icon = FrIcons.ChevronRight, onClick = {}, contentDescription = "Next")
            FrIconButton(icon = FrIcons.Settings, onClick = {}, contentDescription = "Disabled", enabled = false)
        }
    }
}

@Composable
private fun ProgressStory() {
    CatalogScene(label = "Spinner — default / tinted") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            FrProgressIndicator(modifier = Modifier.size(40.dp))
            FrProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ShutterStory() {
    CatalogScene(label = "Shutter — enabled / disabled") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            FrShutterButton(onClick = {})
            FrShutterButton(onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun SpacerStory() {
    val items = listOf(
        "xxs" to Spacing.xxs,
        "xs"  to Spacing.xs,
        "sm"  to Spacing.sm,
        "md"  to Spacing.md,
        "lg"  to Spacing.lg,
        "xl"  to Spacing.xl,
        "xxl" to Spacing.xxl,
    )
    CatalogScene(label = "Heights — Spacing tokens") {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEach { (label, dp) ->
                FrText(
                    text = "Spacing.$label · $dp",
                    style = MaterialTheme.typography.labelSmall,
                )
                FrSpacer(size = dp)
                FrDivider()
            }
        }
    }
}

@Composable
private fun TextStory() {
    CatalogSectionHeader("Material ramp")
    CatalogScene(label = "FrText with M3 typography") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FrText("Display small",   style = MaterialTheme.typography.displaySmall)
            FrText("Headline medium", style = MaterialTheme.typography.headlineMedium)
            FrText("Title large",     style = MaterialTheme.typography.titleLarge)
            FrText("Body large — the quick brown rat leaps over the lazy plate.",
                style = MaterialTheme.typography.bodyLarge)
            FrText("LABEL LARGE", style = MaterialTheme.typography.labelLarge)
        }
    }
    CatalogSectionHeader("FrTextStyles")
    CatalogScene(label = "Tabular numerals") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FrText("12345", style = FrTextStyles.statNumber)
            FrText("Streak · 42 days", style = FrTextStyles.statNumberSmall)
        }
    }
}

@Composable
private fun TextFieldStory() {
    var dish by remember { mutableStateOf("Roast chicken") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Empty + placeholder") {
            FrTextField(value = "", onValueChange = {}, label = "Dish name", placeholder = "Enter dish name")
        }
        CatalogScene(label = "Filled (live)") {
            FrTextField(value = dish, onValueChange = { dish = it }, label = "Dish name")
        }
        CatalogScene(label = "Error") {
            FrTextField(value = "Bad input", onValueChange = {}, label = "Dish name", isError = true)
        }
        CatalogScene(label = "Disabled") {
            FrTextField(value = "Locked", onValueChange = {}, label = "Dish name", enabled = false)
        }
        CatalogScene(label = "Multiline") {
            FrTextField(
                value = "Notes line 1\nNotes line 2",
                onValueChange = {},
                label = "Notes",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

