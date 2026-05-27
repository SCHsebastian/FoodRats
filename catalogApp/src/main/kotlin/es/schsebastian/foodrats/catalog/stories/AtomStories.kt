package es.schsebastian.foodrats.catalog.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrChip
import es.schsebastian.foodrats.core.designsystem.atoms.FrCrownBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrDivider
import es.schsebastian.foodrats.core.designsystem.atoms.FrFilterChip
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlameBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlameIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrGlassPill
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrShutterButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrSparkline
import es.schsebastian.foodrats.core.designsystem.atoms.FrSpacer
import es.schsebastian.foodrats.core.designsystem.atoms.FrSwitch
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.atoms.FrUploadProgressBar
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

internal fun atomStories(): List<CatalogEntry> = listOf(
    CatalogEntry("atom.avatar",        CatalogGroup.ATOMS, "FrAvatar",        "Initial-circle avatar at sm/md/lg sizes") { AvatarStory() },
    CatalogEntry("atom.button",        CatalogGroup.ATOMS, "FrButton",        "Primary / Secondary / Ghost · enabled & disabled") { ButtonStory() },
    CatalogEntry("atom.chip",          CatalogGroup.ATOMS, "FrChip",          "One-shot assistive chip (Role.Button)") { ChipStory() },
    CatalogEntry("atom.filterchip",    CatalogGroup.ATOMS, "FrFilterChip",    "Selection chip (Role.Checkbox) with check-on-selected") { FilterChipStory() },
    CatalogEntry("atom.divider",       CatalogGroup.ATOMS, "FrDivider",       "Thickness & tint variants") { DividerStory() },
    CatalogEntry("atom.icon",          CatalogGroup.ATOMS, "FrIcon",          "Tintable icon at three sizes") { IconStory() },
    CatalogEntry("atom.icons",         CatalogGroup.ATOMS, "FrIcons",         "Project-wide ImageVector catalog (core-only set)") { IconsCatalogStory() },
    CatalogEntry("atom.iconbutton",    CatalogGroup.ATOMS, "FrIconButton",    "Icon-only button — back, settings, chevrons") { IconButtonStory() },
    CatalogEntry("atom.progress",      CatalogGroup.ATOMS, "FrProgressIndicator", "Default + tinted spinners") { ProgressStory() },
    CatalogEntry("atom.shutter",       CatalogGroup.ATOMS, "FrShutterButton", "Capture-screen shutter — enabled & disabled") { ShutterStory() },
    CatalogEntry("atom.spacer",        CatalogGroup.ATOMS, "FrSpacer",        "Spacing token visualization") { SpacerStory() },
    CatalogEntry("atom.text",          CatalogGroup.ATOMS, "FrText",          "Wraps Material Text with FrTextStyles") { TextStory() },
    CatalogEntry("atom.textfield",     CatalogGroup.ATOMS, "FrTextField",     "OutlinedTextField with error / disabled states") { TextFieldStory() },
    CatalogEntry("atom.crownbadge",    CatalogGroup.ATOMS, "FrCrownBadge",    "Circular crown badge — primary / tertiary tints") { CrownBadgeStory() },
    CatalogEntry("atom.flameicon",     CatalogGroup.ATOMS, "FrFlameIcon",     "Flame with urgency-driven pulse (0 → 1)") { FlameIconStory() },
    CatalogEntry("atom.shimmerbox",    CatalogGroup.ATOMS, "FrShimmerBox",    "Skeleton with horizontal shimmer sweep") { ShimmerBoxStory() },
    CatalogEntry("atom.uploadprogress", CatalogGroup.ATOMS, "FrUploadProgressBar", "Top-of-screen indeterminate bar that slides in while uploads run") { UploadProgressBarStory() },
    CatalogEntry("atom.logo",          CatalogGroup.ATOMS, "FrLogo",          "FoodRats canvas mark — plate + ears at three sizes") { LogoStory() },
    CatalogEntry("atom.card",          CatalogGroup.ATOMS, "FrCard",          "Rounded surface container — static or clickable with press lift") { CardStory() },
    CatalogEntry("atom.sparkline",     CatalogGroup.ATOMS, "FrSparkline",     "Tiny inline trend chart for stat tiles") { SparklineStory() },
    CatalogEntry("atom.flamebadge",    CatalogGroup.ATOMS, "FrFlameBadge",    "🔥 streak pill on the streakHot semantic color") { FlameBadgeStory() },
    CatalogEntry("atom.glasspill",     CatalogGroup.ATOMS, "FrGlassPill",     "Translucent circular overlay for in-photo back / close") { GlassPillStory() },
    CatalogEntry("atom.switch",        CatalogGroup.ATOMS, "FrSwitch",        "Brand-colored toggle — on / off / disabled") { SwitchStory() },
)

@Composable
private fun SwitchStory() {
    var on by remember { mutableStateOf(true) }
    CatalogScene(label = "Interactive · on / off / disabled") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrSwitch(checked = on, onCheckedChange = { on = it })
            FrSwitch(checked = false, onCheckedChange = {})
            FrSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

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
        CatalogScene(label = "Image loaded") {
            FrAvatar(initials = "sc", imageUrl = "https://placebear.com/120/120")
        }
        CatalogScene(label = "Slow/failed load → initials fallback") {
            FrAvatar(initials = "sc", imageUrl = "https://example.invalid/missing.jpg")
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(
            label = "One-shot actions",
            description = "FrChip is AssistChip. Use FrFilterChip for selection.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FrChip(label = "Edit",     onClick = {})
                FrChip(label = "Share",    onClick = {})
                FrChip(label = "Try again", onClick = {})
            }
        }
        CatalogScene(label = "Disabled") {
            FrChip(label = "Off", onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun FilterChipStory() {
    var picks by remember { mutableStateOf(setOf("Salad")) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(
            label = "Selectable — Role.Checkbox",
            description = "Tap toggles state. Selected chips show a check.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                listOf("Pizza", "Salad", "Pasta", "Vegan").forEach { tag ->
                    FrFilterChip(
                        label = tag,
                        selected = tag in picks,
                        onClick = {
                            picks = if (tag in picks) picks - tag else picks + tag
                        },
                    )
                }
            }
        }
        CatalogScene(label = "Disabled") {
            FrFilterChip(label = "Off", selected = true, onClick = {}, enabled = false)
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
        "Camera"        to FrIcons.Camera,
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
            FrShutterButton(onClick = {}, contentDescription = "Take photo")
            FrShutterButton(onClick = {}, contentDescription = "Take photo", enabled = false)
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

@Composable
private fun CrownBadgeStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Default (primary)") { FrCrownBadge() }
        CatalogScene(label = "Tertiary (bronze)") {
            FrCrownBadge(
                background = MaterialTheme.colorScheme.tertiary,
                iconTint = MaterialTheme.colorScheme.onTertiary,
            )
        }
        CatalogScene(label = "Large") {
            FrCrownBadge(iconSize = 40.dp)
        }
    }
}

@Composable
private fun FlameIconStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Slow pulse (urgency = 0)") {
            FrFlameIcon(urgency = 0f, tint = MaterialTheme.colorScheme.primary)
        }
        CatalogScene(label = "Medium pulse (urgency = 0.5)") {
            FrFlameIcon(urgency = 0.5f, tint = MaterialTheme.colorScheme.primary)
        }
        CatalogScene(label = "Fast pulse (urgency = 1.0)") {
            FrFlameIcon(urgency = 1f, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun UploadProgressBarStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Visible — primary tint (default)") {
            FrUploadProgressBar(visible = true)
        }
        CatalogScene(label = "Visible — semantic 'success' tint") {
            FrUploadProgressBar(
                visible = true,
                color = es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors.current.success,
            )
        }
        CatalogScene(label = "Hidden (no bar drawn)") {
            FrUploadProgressBar(visible = false)
        }
        CatalogScene(label = "Toggle live") {
            var on by remember { mutableStateOf(true) }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FrUploadProgressBar(visible = on)
                FrButton(label = if (on) "Hide" else "Show", onClick = { on = !on })
            }
        }
    }
}

@Composable
private fun LogoStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(
            label = "Sizes — 48 / 96 / 144 dp",
            description = "Canvas-drawn plate + three ears. Defaults are the brand palette (concrete plate, ember ears).",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrLogo(size = 48.dp)
                FrLogo(size = 96.dp)
                FrLogo(size = 144.dp)
            }
        }
        CatalogScene(label = "Tinted — primary plate / onPrimary ears") {
            FrLogo(
                size = 96.dp,
                plateColor = MaterialTheme.colorScheme.primary,
                earColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ShimmerBoxStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Rectangle") {
            FrShimmerBox(modifier = Modifier.fillMaxWidth().size(width = 320.dp, height = 60.dp))
        }
        CatalogScene(label = "Rounded card silhouette") {
            FrShimmerBox(
                modifier = Modifier.fillMaxWidth().size(width = 320.dp, height = 120.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
        }
        CatalogScene(label = "Circle (avatar silhouette)") {
            FrShimmerBox(
                modifier = Modifier.size(48.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
            )
        }
    }
}

@Composable
private fun CardStory() {
    CatalogScene(label = "Static / clickable") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FrCard(modifier = Modifier.fillMaxWidth()) {
                FrText("Static card", style = MaterialTheme.typography.titleMedium)
                FrText("elevation-1, no press feedback", style = MaterialTheme.typography.bodySmall)
            }
            FrCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                FrText("Clickable card", style = MaterialTheme.typography.titleMedium)
                FrText("press → scale 0.98 + lift to 4dp", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SparklineStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Rising trend") {
            FrSparkline(data = listOf(7.4f, 8f, 8.6f, 7f, 9.2f, 8.5f, 9.2f), width = 140.dp, height = 40.dp)
        }
        CatalogScene(label = "Tinted (secondary)") {
            FrSparkline(
                data = listOf(3f, 5f, 4f, 6f, 5.5f, 7f),
                color = MaterialTheme.colorScheme.secondary,
                width = 140.dp,
                height = 40.dp,
            )
        }
        CatalogScene(label = "Single point → flat mid-line") {
            FrSparkline(data = listOf(5f), width = 140.dp, height = 40.dp)
        }
    }
}

@Composable
private fun FlameBadgeStory() {
    CatalogScene(
        label = "Streak lengths",
        description = "Hidden entirely at 0 — the fourth pill below isn't drawn.",
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrFlameBadge(days = 1)
            FrFlameBadge(days = 7)
            FrFlameBadge(days = 42)
            FrFlameBadge(days = 0)
        }
    }
}

@Composable
private fun GlassPillStory() {
    CatalogScene(label = "Over a photo surface", padding = PaddingValues(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = 320.dp, height = 120.dp)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.TopStart,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrGlassPill(icon = FrIcons.Back, onClick = {}, contentDescription = "Back")
                FrGlassPill(icon = FrIcons.Close, onClick = {}, contentDescription = "Close")
            }
        }
    }
}

