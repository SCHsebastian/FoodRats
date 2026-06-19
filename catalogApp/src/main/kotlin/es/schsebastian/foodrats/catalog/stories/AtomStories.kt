package es.schsebastian.foodrats.catalog.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.catalog.components.CatalogScene
import es.schsebastian.foodrats.catalog.components.CatalogSceneSplit
import es.schsebastian.foodrats.catalog.components.CatalogSectionHeader
import es.schsebastian.foodrats.catalog.registry.CatalogEntry
import es.schsebastian.foodrats.catalog.registry.CatalogGroup
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadgeTier
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrChip
import es.schsebastian.foodrats.core.designsystem.atoms.FrCrownBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrDivider
import es.schsebastian.foodrats.core.designsystem.atoms.FrFilterChip
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlameIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrGlassPill
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrQrCode
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrSparkline
import es.schsebastian.foodrats.core.designsystem.atoms.FrStoryProgressBar
import es.schsebastian.foodrats.core.designsystem.atoms.FrStoryScaffold
import es.schsebastian.foodrats.core.designsystem.atoms.FrSwitch
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.atoms.FrUploadProgressBar
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalMinotaurMode
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
    CatalogEntry("atom.text",          CatalogGroup.ATOMS, "FrText",          "Wraps Material Text with FrTextStyles") { TextStory() },
    CatalogEntry("atom.textfield",     CatalogGroup.ATOMS, "FrTextField",     "OutlinedTextField with error / disabled states") { TextFieldStory() },
    CatalogEntry("atom.crownbadge",    CatalogGroup.ATOMS, "FrCrownBadge",    "Circular crown badge — primary / tertiary tints") { CrownBadgeStory() },
    CatalogEntry("atom.flameicon",     CatalogGroup.ATOMS, "FrFlameIcon",     "Flame with urgency-driven pulse (0 → 1)") { FlameIconStory() },
    CatalogEntry("atom.shimmerbox",    CatalogGroup.ATOMS, "FrShimmerBox",    "Skeleton with horizontal shimmer sweep") { ShimmerBoxStory() },
    CatalogEntry("atom.uploadprogress", CatalogGroup.ATOMS, "FrUploadProgressBar", "Top-of-screen indeterminate bar that slides in while uploads run") { UploadProgressBarStory() },
    CatalogEntry("atom.logo",          CatalogGroup.ATOMS, "FrLogo",          "FoodRats canvas mark — plate + ears at three sizes") { LogoStory() },
    CatalogEntry("atom.card",          CatalogGroup.ATOMS, "FrCard",          "Rounded surface container — static or clickable with press lift") { CardStory() },
    CatalogEntry("atom.card.fur",      CatalogGroup.ATOMS, "FrCard (Minotaur)", "Hidden Minotaur mode — furry edge via LocalMinotaurMode") { MinotaurCardStory() },
    CatalogEntry("atom.sparkline",     CatalogGroup.ATOMS, "FrSparkline",     "Tiny inline trend chart for stat tiles") { SparklineStory() },
    CatalogEntry("atom.qrcode",        CatalogGroup.ATOMS, "FrQrCode",        "Pure-Kotlin QR encoder rendered on Canvas — shareable invite link") { QrCodeStory() },
    CatalogEntry("atom.glasspill",     CatalogGroup.ATOMS, "FrGlassPill",     "Translucent circular overlay for in-photo back / close") { GlassPillStory() },
    CatalogEntry("atom.switch",        CatalogGroup.ATOMS, "FrSwitch",        "Brand-colored toggle — on / off / disabled") { SwitchStory() },
    CatalogEntry("atom.badge",         CatalogGroup.ATOMS, "FrBadge",         "Achievement badge — earned (vivid) vs locked (dimmed + progress ring), tiers") { BadgeStory() },
    CatalogEntry("atom.storyprogress", CatalogGroup.ATOMS, "FrStoryProgressBar", "Instagram-Stories segmented progress header — one pill per scene, active pill fills") { StoryProgressBarStory() },
    CatalogEntry("atom.storyscaffold", CatalogGroup.ATOMS, "FrStoryScaffold",  "Full-screen story chrome — progress header, close, tap-prev / tap-next zones, hold-to-pause, overlay action slot") { StoryScaffoldStory() },
)

@Composable
private fun StoryScaffoldStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogScene(label = "Scene 2 of 4 @ 60% (sized to a phone-shaped frame)") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) {
                FrStoryScaffold(
                    segmentCount = 4,
                    currentIndex = 1,
                    currentProgress = 0.6f,
                    onPrev = {},
                    onNext = {},
                    onClose = {},
                    progressContentDescription = "Story progress",
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        FrText(text = "Scene content", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        CatalogScene(label = "With an overlay action slot — the button gets the tap, not the advance zone") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) {
                FrStoryScaffold(
                    segmentCount = 4,
                    currentIndex = 3,
                    currentProgress = 1f,
                    onPrev = {},
                    onNext = {},
                    onClose = {},
                    progressContentDescription = "Story progress",
                    action = { FrButton(label = "Share this recap", onClick = {}) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        FrText(text = "Your week", color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryProgressBarStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogScene(label = "5 scenes · scene 3 @ 40% (over dark surface)") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim)
                    .padding(Spacing.md),
            ) {
                FrStoryProgressBar(segmentCount = 5, currentIndex = 2, currentProgress = 0.4f)
            }
        }
        CatalogScene(label = "First scene starting · last scene complete (brand tint)") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrStoryProgressBar(
                    segmentCount = 4,
                    currentIndex = 0,
                    currentProgress = 0.05f,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    fillColor = MaterialTheme.colorScheme.primary,
                )
                FrStoryProgressBar(
                    segmentCount = 4,
                    currentIndex = 3,
                    currentProgress = 1f,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    fillColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

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
private fun BadgeStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogSceneSplit(label = "Earned (vivid) vs locked (dimmed + progress ring)") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrBadge(
                    icon = FrIcons.Trophy,
                    title = "First Plate",
                    earned = true,
                    progressFraction = 1f,
                    caption = "Earned May 4",
                )
                FrBadge(
                    icon = FrIcons.Restaurant,
                    title = "Home Cook",
                    earned = false,
                    progressFraction = 0.6f,
                    caption = "30 / 50",
                )
            }
        }
        CatalogScene(label = "Tiers — Bronze / Silver / Gold (earned)") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrBadge(icon = FrIcons.Eco, title = "Bronze", earned = true, progressFraction = 1f, tier = FrBadgeTier.Bronze)
                FrBadge(icon = FrIcons.Eco, title = "Silver", earned = true, progressFraction = 1f, tier = FrBadgeTier.Silver)
                FrBadge(icon = FrIcons.Eco, title = "Gold", earned = true, progressFraction = 1f, tier = FrBadgeTier.Gold)
            }
        }
        CatalogScene(label = "Locked progress sweep — 10% / 50% / 90%") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FrBadge(icon = FrIcons.Sun, title = "Early Bird", earned = false, progressFraction = 0.1f, caption = "1 / 10")
                FrBadge(icon = FrIcons.Moon, title = "Night Owl", earned = false, progressFraction = 0.5f, caption = "5 / 10")
                FrBadge(icon = FrIcons.Flame, title = "On a Roll", earned = false, progressFraction = 0.9f, caption = "9 / 10")
            }
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
                            Spacer(modifier = Modifier.size(Spacing.xxs))
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
private fun QrCodeStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Invite link") {
            FrQrCode(content = "https://foodrats.app/invite/AB2K9P", size = 200.dp)
        }
        CatalogScene(label = "Smaller (140dp)") {
            FrQrCode(content = "https://foodrats.app/invite/AB2K9P", size = 140.dp)
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

@Composable
private fun MinotaurCardStory() {
    CatalogScene(label = "Minotaur mode ON — furry pelt + neon rim") {
        CompositionLocalProvider(LocalMinotaurMode provides true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim)
                    .padding(vertical = Spacing.xxl, horizontal = Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                FrCard(modifier = Modifier.width(220.dp)) {
                    FrText("Furry card", style = MaterialTheme.typography.titleMedium)
                    FrText("3-finger long-press unlocks this app-wide", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

