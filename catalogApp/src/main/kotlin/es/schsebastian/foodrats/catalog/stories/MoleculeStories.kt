package es.schsebastian.foodrats.catalog.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.catalog.components.CatalogScene
import es.schsebastian.foodrats.catalog.registry.CatalogEntry
import es.schsebastian.foodrats.catalog.registry.CatalogGroup
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarWithName
import es.schsebastian.foodrats.core.designsystem.molecules.FrComposerHeroCard
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.molecules.FrScorePicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsDivider
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsRow
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsRowTone
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsSection
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsSectionTone
import es.schsebastian.foodrats.core.designsystem.molecules.FrStarRatingPicker
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

internal fun moleculeStories(): List<CatalogEntry> = listOf(
    CatalogEntry("molecule.avatarwithname", CatalogGroup.MOLECULES, "FrAvatarWithName", "Avatar + display name row") { AvatarWithNameStory() },
    CatalogEntry("molecule.emptystate",     CatalogGroup.MOLECULES, "FrEmptyState",     "Empty-state with icon, headline, optional CTA") { EmptyStateStory() },
    CatalogEntry("molecule.errorbanner",    CatalogGroup.MOLECULES, "FrErrorBanner",    "Tinted banner for error messages") { ErrorBannerStory() },
    CatalogEntry("molecule.labeledtextfield", CatalogGroup.MOLECULES, "FrLabeledTextField", "Text field with label and optional helper") { LabeledTextFieldStory() },
    CatalogEntry("molecule.scorebadge",     CatalogGroup.MOLECULES, "FrScoreBadge",     "Circular 1..10 score badge") { ScoreBadgeStory() },
    CatalogEntry("molecule.scorepicker",    CatalogGroup.MOLECULES, "FrScorePicker",    "Chip row picker for 1..10 score") { ScorePickerStory() },
    CatalogEntry("molecule.starrating",     CatalogGroup.MOLECULES, "FrStarRatingPicker", "1..5 picker for tap-to-rate flows") { StarRatingStory() },
    CatalogEntry("molecule.confirmdialog",  CatalogGroup.MOLECULES, "FrConfirmDialog",    "Affirmative/destructive confirmation dialog") { ConfirmDialogStory() },
    CatalogEntry("molecule.composerhero",   CatalogGroup.MOLECULES, "FrComposerHeroCard", "Photo hero with rounded clip + entry scale animation") { ComposerHeroCardStory() },
    CatalogEntry("molecule.avatarpicker",   CatalogGroup.MOLECULES, "FrAvatarPicker",     "Avatar preview + change-avatar button (initials fallback, busy state)") { AvatarPickerStory() },
    CatalogEntry("molecule.settings-section", CatalogGroup.MOLECULES, "FrSettingsSection", "Grouped settings card with header + rounded surface") { SettingsSectionStory() },
    CatalogEntry("molecule.settings-row",     CatalogGroup.MOLECULES, "FrSettingsRow",     "Settings row: icon + title + subtitle + trailing slot") { SettingsRowStory() },
)

@Composable
private fun AvatarWithNameStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Short / long names — ellipsizes at one line") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrAvatarWithName(initials = "SC", name = "Sebastián Cardona")
                FrAvatarWithName(initials = "AN", name = "Anika", subtitle = "Last meal · 2h ago")
                FrAvatarWithName(
                    initials = "RK",
                    name = "Reggie K. — the long-display-name that has to ellipsize",
                )
            }
        }
        CatalogScene(label = "With trailing slot (score badge)") {
            FrAvatarWithName(
                initials = "SC",
                name = "Sebastián Cardona",
                trailing = { FrScoreBadge(score = 8, contentDescription = "Score 8 of 10") },
            )
        }
        CatalogScene(
            label = "Constrained 240dp — text ellipsizes",
            padding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
        ) {
            FrAvatarWithName(
                modifier = Modifier.width(240.dp),
                initials = "RK",
                name = "Reggie K. — the very very long display name that ellipsizes",
                trailing = { FrScoreBadge(score = 9) },
            )
        }
    }
}

@Composable
private fun EmptyStateStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogScene(label = "With CTA") {
            FrEmptyState(
                icon = FrIcons.AddPhoto,
                headline = "No meals yet today",
                subtext = "Capture your first plate to start a streak.",
                cta = { FrButton(label = "Capture meal", onClick = {}) },
            )
        }
        CatalogScene(label = "No CTA") {
            FrEmptyState(
                icon = FrIcons.CameraOff,
                headline = "Camera unavailable",
                subtext = "Pick a photo from the gallery instead.",
            )
        }
    }
}

@Composable
private fun ErrorBannerStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Short message") {
            FrErrorBanner(text = "Could not load feed. Check your connection.")
        }
        CatalogScene(label = "Long message — wraps") {
            FrErrorBanner(
                text = "Long message: you've already published a meal today — try again tomorrow when the streak rolls over.",
            )
        }
    }
}

@Composable
private fun LabeledTextFieldStory() {
    var crewName by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("not-an-email") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Empty + helper") {
            FrLabeledTextField(
                label = "Dish name",
                value = "",
                onValueChange = {},
                helper = "What did you eat?",
            )
        }
        CatalogScene(label = "Filled (live)") {
            FrLabeledTextField(
                label = "Crew name",
                value = crewName,
                onValueChange = { crewName = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CatalogScene(label = "Error") {
            FrLabeledTextField(
                label = "Email",
                value = email,
                onValueChange = { email = it },
                helper = "Enter a valid email address.",
                isError = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScoreBadgeStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(
            label = "Score 1 → 10 — width-locked",
            description = "Single-digit and double-digit badges share the same footprint.",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrScoreBadge(score = 1)
                FrScoreBadge(score = 5)
                FrScoreBadge(score = 7)
                FrScoreBadge(score = 8)
                FrScoreBadge(score = 10)
            }
        }
        CatalogScene(label = "With contentDescription (accessibility)") {
            FrScoreBadge(score = 8, contentDescription = "Score 8 of 10")
        }
    }
}

@Composable
private fun ScorePickerStory() {
    var pick by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogScene(
            label = "Live — big readout + cells",
            description = "Single-select group (Role.RadioButton). Cells are uniform-width.",
        ) {
            FrScorePicker(
                value = pick,
                onChange = { pick = it },
                groupContentDescription = "Score, 1 to 10",
                cellContentDescriptionFor = { "Score $it of 10" },
            )
        }
        CatalogScene(label = "Pre-selected = 7") {
            FrScorePicker(value = 7, onChange = {})
        }
        CatalogScene(
            label = "Narrow 280dp — wraps to 2 rows",
            description = "FlowRow handles widths that can't fit all cells in one line.",
        ) {
            FrScorePicker(
                modifier = Modifier.width(280.dp),
                value = 4,
                onChange = {},
            )
        }
        CatalogScene(label = "Disabled") {
            FrScorePicker(value = 6, onChange = {}, enabled = false)
        }
    }
}

@Composable
private fun StarRatingStory() {
    var rating by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogScene(
            label = "Live — fill stars up to selected",
            description = "Selected stars use FrSemanticColors.celebration (honey/gold).",
        ) {
            FrStarRatingPicker(
                onSelect = { rating = it },
                value = rating,
                groupContentDescription = "Rate this meal, 1 to 5 stars",
                starLabel = { i -> "$i ${if (i == 1) "star" else "stars"}" },
            )
        }
        CatalogScene(label = "Pre-filled = 3") {
            FrStarRatingPicker(onSelect = {}, value = 3)
        }
        CatalogScene(label = "Empty (value = 0)") {
            FrStarRatingPicker(onSelect = {}, value = 0)
        }
    }
}

@Composable
private fun ConfirmDialogStory() {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Open dialog (tap to preview)") {
            FrButton(label = "Show confirm dialog", onClick = { open = true })
        }
        if (open) {
            FrConfirmDialog(
                title = "Publish this meal?",
                message = "Your photo and description will be shared with the crew.",
                confirmLabel = "Publish",
                dismissLabel = "Cancel",
                onConfirm = { open = false },
                onDismiss = { open = false },
            )
        }
    }
}

@Composable
private fun AvatarPickerStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(
            label = "Initials fallback — idle",
            description = "Avatar slot + 'Change' button. Tapping either calls onPickClick().",
        ) {
            FrAvatarPicker(
                initials = "SC",
                avatarUrl = null,
                onPickClick = {},
                busy = false,
                changeLabel = "Change",
                uploadingLabel = "Uploading…",
            )
        }
        CatalogScene(label = "With image — idle") {
            FrAvatarPicker(
                initials = "SC",
                avatarUrl = "https://placebear.com/200/200",
                onPickClick = {},
                busy = false,
                changeLabel = "Change",
                uploadingLabel = "Uploading…",
            )
        }
        CatalogScene(label = "Busy — disables tap, swaps label") {
            FrAvatarPicker(
                initials = "SC",
                avatarUrl = null,
                onPickClick = {},
                busy = true,
                changeLabel = "Change",
                uploadingLabel = "Uploading…",
            )
        }
    }
}

@Composable
private fun SettingsSectionStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogScene(label = "Neutral — drilldown + switch + value") {
            FrSettingsSection(title = "Preferences") {
                FrSettingsRow(title = "Theme", subtitle = "System default", icon = FrIcons.Settings, onClick = {})
                FrSettingsDivider()
                var checked by remember { mutableStateOf(true) }
                FrSettingsRow(
                    title = "Notifications",
                    icon = FrIcons.Settings,
                    trailing = { Switch(checked = checked, onCheckedChange = { checked = it }) },
                )
                FrSettingsDivider()
                FrSettingsRow(title = "Language", icon = FrIcons.Settings, trailing = { Text("English") })
            }
        }
        CatalogScene(label = "Danger — red border + tinted header") {
            FrSettingsSection(title = "Danger Zone", tone = FrSettingsSectionTone.Danger) {
                FrSettingsRow(title = "Sign out", icon = FrIcons.Back, onClick = {})
                FrSettingsDivider()
                FrSettingsRow(
                    title = "Delete account",
                    subtitle = "Permanent. Removes meals, ratings, memberships.",
                    icon = FrIcons.Delete,
                    tone = FrSettingsRowTone.Danger,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun SettingsRowStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CatalogScene(label = "Drilldown — chevron auto-appended") {
            FrSettingsSection(title = "Drilldown rows") {
                FrSettingsRow(title = "Theme", subtitle = "System default", icon = FrIcons.Settings, onClick = {})
                FrSettingsDivider()
                FrSettingsRow(title = "About", icon = FrIcons.Settings, onClick = {})
            }
        }
        CatalogScene(label = "With trailing slot — value text") {
            FrSettingsSection(title = "Value rows") {
                FrSettingsRow(title = "Language", icon = FrIcons.Settings, trailing = { Text("Español") })
                FrSettingsDivider()
                FrSettingsRow(title = "Email", icon = FrIcons.Settings, trailing = { Text("seb@example.com") })
            }
        }
        CatalogScene(label = "Danger tone + disabled") {
            FrSettingsSection(title = "Danger rows") {
                FrSettingsRow(
                    title = "Delete account",
                    icon = FrIcons.Delete,
                    tone = FrSettingsRowTone.Danger,
                    onClick = {},
                )
                FrSettingsDivider()
                FrSettingsRow(
                    title = "Delete account (disabled)",
                    icon = FrIcons.Delete,
                    tone = FrSettingsRowTone.Danger,
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun ComposerHeroCardStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Empty — placeholder colour") {
            FrComposerHeroCard(contentKey = null, modifier = Modifier.width(280.dp)) { }
        }
        CatalogScene(label = "Filled — entry scale animation") {
            FrComposerHeroCard(contentKey = "demo", modifier = Modifier.width(280.dp)) {
                FrAvatarWithName(initials = "FR", name = "Demo plate")
            }
        }
    }
}

