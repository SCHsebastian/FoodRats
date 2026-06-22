package es.schsebastian.foodrats.catalog.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.catalog.components.CatalogScene
import es.schsebastian.foodrats.catalog.components.CatalogSectionHeader
import es.schsebastian.foodrats.catalog.registry.CatalogEntry
import es.schsebastian.foodrats.catalog.registry.CatalogGroup
import es.schsebastian.foodrats.catalog.theme.ThemeMode
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrAvatarRing
import es.schsebastian.foodrats.core.designsystem.structural.FrBadgeDisc
import es.schsebastian.foodrats.core.designsystem.structural.FrBarTrack
import es.schsebastian.foodrats.core.designsystem.structural.FrBentoGrid
import es.schsebastian.foodrats.core.designsystem.structural.FrBentoItem
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrChipTone
import es.schsebastian.foodrats.core.designsystem.structural.FrDock
import es.schsebastian.foodrats.core.designsystem.structural.FrDockItem
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrFlameBadge
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassAvatar
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassDialog
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassRadio
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassSheet
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassToggle
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrMetric
import es.schsebastian.foodrats.core.designsystem.structural.FrMetricSize
import es.schsebastian.foodrats.core.designsystem.structural.FrMicroRow
import es.schsebastian.foodrats.core.designsystem.structural.FrScoreDisc
import es.schsebastian.foodrats.core.designsystem.structural.FrScoreTone
import es.schsebastian.foodrats.core.designsystem.structural.FrScrim
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralChip
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralRow
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.FrTileTone
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Catalog entries for the opt-in "Structural" variant. Everything is dark-first and rendered over a
 * media floor (`StructuralColors.stageFloor`) so the frosted translucency and depth read correctly.
 * Each scene is `lockedTheme = ThemeMode.Dark` — the matte production DS is reviewed in the other
 * groups; this group is the bolder, media-forward concept made real in Compose.
 */
internal fun structuralStories(): List<CatalogEntry> = listOf(
    CatalogEntry("structural.mediafloor", CatalogGroup.STRUCTURAL, "FrMediaFloor", "Edge-to-edge media foundation: field + dish moods") { MediaFloorStory() },
    CatalogEntry("structural.scrim", CatalogGroup.STRUCTURAL, "FrScrim", "Legibility washes (Standard / Even / Photo)") { ScrimStory() },
    CatalogEntry("structural.tile", CatalogGroup.STRUCTURAL, "FrGlassTile", "Frosted strata — depth (Deep/Near) + Ember/Olive tones") { TileStory() },
    CatalogEntry("structural.bento", CatalogGroup.STRUCTURAL, "FrBentoGrid", "Asymmetric grid — tile size = data priority") { BentoStory() },
    CatalogEntry("structural.type", CatalogGroup.STRUCTURAL, "Structural type", "Extreme contrast: metric vs micro") { TypeStory() },
    CatalogEntry("structural.metric", CatalogGroup.STRUCTURAL, "FrMetric", "Oversized tabular number with unit suffix") { MetricStory() },
    CatalogEntry("structural.micro", CatalogGroup.STRUCTURAL, "FrEyebrow / FrMicroRow", "Eyebrow + dot-separated metadata array") { MicroStory() },
    CatalogEntry("structural.scoredisc", CatalogGroup.STRUCTURAL, "FrScoreDisc", "Score badge — Olive / Hot / Muted") { ScoreDiscStory() },
    CatalogEntry("structural.avatar", CatalogGroup.STRUCTURAL, "FrGlassAvatar", "Avatar with accent rings + sizes") { AvatarStory2() },
    CatalogEntry("structural.chip", CatalogGroup.STRUCTURAL, "FrStructuralChip", "Frosted pill — selected / Ember / compact") { ChipStory2() },
    CatalogEntry("structural.flame", CatalogGroup.STRUCTURAL, "FrFlameBadge", "Streak flame pill") { FlameStory() },
    CatalogEntry("structural.button", CatalogGroup.STRUCTURAL, "FrGlassButton", "Primary / Ember / Glass / Ghost / Danger") { ButtonStory2() },
    CatalogEntry("structural.circlebutton", CatalogGroup.STRUCTURAL, "FrGlassCircleButton", "Dark frosted floating chrome (back / close)") { CircleButtonStory() },
    CatalogEntry("structural.field", CatalogGroup.STRUCTURAL, "FrUnderlineField", "Zero-box underline text field") { FieldStory() },
    CatalogEntry("structural.toggle", CatalogGroup.STRUCTURAL, "FrGlassToggle", "Sliding switch — off / on") { ToggleStory() },
    CatalogEntry("structural.radio", CatalogGroup.STRUCTURAL, "FrGlassRadio", "Ring radio — selected / unselected") { RadioStory() },
    CatalogEntry("structural.row", CatalogGroup.STRUCTURAL, "FrStructuralRow", "Divider-less rows (hairline light)") { RowStory() },
    CatalogEntry("structural.dock", CatalogGroup.STRUCTURAL, "FrDock", "Floating frosted nav + raised FAB") { DockStory() },
    CatalogEntry("structural.containers", CatalogGroup.STRUCTURAL, "FrGlassSheet / FrGlassDialog", "Frosted sheet + dialog") { ContainersStory() },
    CatalogEntry("structural.passport", CatalogGroup.STRUCTURAL, "FrBadgeDisc / FrBarTrack", "Passport badge disc + progress track") { PassportStory() },
)

// --- shared backdrop -------------------------------------------------------------------------------

@Composable
private fun StructuralStage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(StructuralColors.stageFloor)
            .padding(Spacing.lg),
        content = content,
    )
}

// --- stories ---------------------------------------------------------------------------------------

@Composable
private fun MediaFloorStory() {
    CatalogScene("Floors", lockedTheme = ThemeMode.Dark) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FloorSwatch("field", Modifier.weight(1f)) { FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.None, dim = 0.1f, scrim = null) }
                FloorSwatch("olive", Modifier.weight(1f)) { FrMediaFloor(brush = StructuralColors.oliveFloor, blur = StructuralBlur.None, dim = 0.1f, scrim = null) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FloorSwatch("ramen", Modifier.weight(1f)) { FrMediaFloor(brush = StructuralColors.dishRamen, blur = StructuralBlur.None, dim = 0.1f, scrim = null) }
                FloorSwatch("salad", Modifier.weight(1f)) { FrMediaFloor(brush = StructuralColors.dishSalad, blur = StructuralBlur.None, dim = 0.1f, scrim = null) }
            }
        }
    }
}

@Composable
private fun FloorSwatch(label: String, modifier: Modifier = Modifier, floor: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(Radius.md)),
    ) {
        floor()
        FrText(
            text = label,
            style = StructuralType.micro,
            color = StructuralColors.foreground,
            modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm),
        )
    }
}

@Composable
private fun ScrimStory() {
    CatalogScene("Scrim styles over a dish floor", lockedTheme = ThemeMode.Dark) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ScrimSwatch("Standard", FrScrimStyle.Standard, Modifier.weight(1f))
            ScrimSwatch("Even", FrScrimStyle.Even, Modifier.weight(1f))
            ScrimSwatch("Photo", FrScrimStyle.Photo, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScrimSwatch(label: String, style: FrScrimStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(Radius.md)),
    ) {
        FrMediaFloor(brush = StructuralColors.dishRamen, blur = StructuralBlur.None, dim = 0f, scrim = null)
        FrScrim(style = style)
        FrText(
            text = label,
            style = StructuralType.micro,
            color = StructuralColors.foreground,
            modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.sm),
        )
    }
}

@Composable
private fun TileStory() {
    CatalogScene("Depth + tone", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FrGlassTile(modifier = Modifier.weight(1f), depth = FrTileDepth.Deep) { FrText("deep", color = StructuralColors.foreground, style = StructuralType.titleMd) }
                    FrGlassTile(modifier = Modifier.weight(1f)) { FrText("default", color = StructuralColors.foreground, style = StructuralType.titleMd) }
                    FrGlassTile(modifier = Modifier.weight(1f), depth = FrTileDepth.Near) { FrText("near", color = StructuralColors.foreground, style = StructuralType.titleMd) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FrGlassTile(modifier = Modifier.weight(1f), tone = FrTileTone.Ember) { FrText("ember", color = StructuralColors.foreground, style = StructuralType.titleMd) }
                    FrGlassTile(modifier = Modifier.weight(1f), tone = FrTileTone.Olive) { FrText("olive", color = StructuralColors.foreground, style = StructuralType.titleMd) }
                }
            }
        }
    }
}

@Composable
private fun BentoStory() {
    CatalogScene("Priority = span", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            FrBentoGrid(
                items = listOf(
                    FrBentoItem(4) {
                        FrGlassTile(depth = FrTileDepth.Near) {
                            FrMetric("9.2", size = FrMetricSize.Md, unit = "/10")
                            FrText("Miso ramen", color = StructuralColors.foreground, style = StructuralType.titleMd)
                        }
                    },
                    FrBentoItem(2) {
                        FrGlassTile { FrMetric("8.4", size = FrMetricSize.Sm) }
                    },
                    FrBentoItem(3) {
                        FrGlassTile(depth = FrTileDepth.Deep) { FrText("Mackerel", color = StructuralColors.foreground, style = StructuralType.titleMd) }
                    },
                    FrBentoItem(3) {
                        FrGlassTile(tone = FrTileTone.Ember) { FrFlameBadge(7) }
                    },
                ),
            )
        }
    }
}

@Composable
private fun TypeStory() {
    CatalogScene("Metric vs micro", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrEyebrow("TODAY · SATURDAY BRUNCH")
                FrText("128", style = StructuralType.metricLg, color = StructuralColors.foreground)
                FrText("Recap of the week", style = StructuralType.titleLg, color = StructuralColors.foreground)
                FrText("Plain prose body sits quietly under the headline.", style = StructuralType.body, color = StructuralColors.foreground)
                FrMicroRow(listOf("ANIKA", "LUNCH", "12:30", "4 VOTES"))
            }
        }
    }
}

@Composable
private fun MetricStory() {
    CatalogScene("Sizes + unit", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrMetric("9.2", size = FrMetricSize.Lg, unit = "/10")
                FrMetric("128", size = FrMetricSize.Md)
                FrMetric("7", size = FrMetricSize.Sm, unit = "day streak")
            }
        }
    }
}

@Composable
private fun MicroStory() {
    CatalogScene("Eyebrow + metadata", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrEyebrow("CREW · SATURDAY BRUNCH")
                FrMicroRow(listOf("REGGIE", "DINNER", "20:10", "3 VOTES"))
            }
        }
    }
}

@Composable
private fun ScoreDiscStory() {
    CatalogScene("Tones", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                FrScoreDisc(9, tone = FrScoreTone.Olive, contentDescription = "Score 9")
                FrScoreDisc(10, tone = FrScoreTone.Hot, contentDescription = "Score 10")
                FrScoreDisc(7, tone = FrScoreTone.Muted, contentDescription = "Score 7")
            }
        }
    }
}

@Composable
private fun AvatarStory2() {
    CatalogScene("Rings + sizes", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                FrGlassAvatar("AN", ring = FrAvatarRing.Olive)
                FrGlassAvatar("RG", size = 32.dp, ring = FrAvatarRing.Ember)
                FrGlassAvatar("JU", size = 56.dp, ring = FrAvatarRing.Moss)
                FrGlassAvatar("SB", size = 56.dp)
            }
        }
    }
}

@Composable
private fun ChipStory2() {
    CatalogScene("Variants", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                FrStructuralChip("BREAKFAST", onClick = {})
                FrStructuralChip("LUNCH", selected = true, onClick = {})
                FrStructuralChip("HOT 9", tone = FrChipTone.Ember)
                FrStructuralChip("NEW", compact = true)
            }
        }
    }
}

@Composable
private fun FlameStory() {
    CatalogScene("Streak", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                FrFlameBadge(7)
                FrFlameBadge(21)
            }
        }
    }
}

@Composable
private fun ButtonStory2() {
    CatalogScene("Tones", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FrGlassButton("Post this plate", onClick = {}, tone = FrButtonTone.Primary, fillWidth = true)
                FrGlassButton("Keep the streak", onClick = {}, tone = FrButtonTone.Ember, fillWidth = true)
                FrGlassButton("Add a photo", onClick = {}, tone = FrButtonTone.Glass, fillWidth = true)
                FrGlassButton("Maybe later", onClick = {}, tone = FrButtonTone.Ghost, fillWidth = true)
                FrGlassButton("Delete meal", onClick = {}, tone = FrButtonTone.Danger, fillWidth = true)
            }
        }
    }
}

@Composable
private fun CircleButtonStory() {
    CatalogScene("Floating chrome", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                FrGlassCircleButton(icon = Icons.Filled.Close, onClick = {}, contentDescription = "Close")
                FrGlassCircleButton(icon = Icons.Filled.Search, onClick = {}, contentDescription = "Search")
                FrGlassCircleButton(icon = Icons.Filled.Close, onClick = {}, contentDescription = "Delete", danger = true)
            }
        }
    }
}

@Composable
private fun FieldStory() {
    var value by remember { mutableStateOf("Saturday Brunch") }
    var password by remember { mutableStateOf("supersecret") }
    var revealed by remember { mutableStateOf(false) }
    CatalogScene("Underline field", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                FrUnderlineFieldHost(value) { value = it }
                // Password variant: obfuscate is toggled by the trailing eye (open = revealed).
                es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField(
                    value = password,
                    onValueChange = { password = it },
                    label = "PASSWORD",
                    placeholder = "Your password",
                    obfuscate = !revealed,
                    trailingIcon = {
                        es.schsebastian.foodrats.core.designsystem.atoms.FrIcon(
                            image = if (revealed) {
                                es.schsebastian.foodrats.core.designsystem.atoms.FrIcons.Visibility
                            } else {
                                es.schsebastian.foodrats.core.designsystem.atoms.FrIcons.VisibilityOff
                            },
                            contentDescription = if (revealed) "Hide password" else "Show password",
                            modifier = Modifier.size(Spacing.lg).clickable { revealed = !revealed },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FrUnderlineFieldHost(value: String, onChange: (String) -> Unit) {
    es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField(
        value = value,
        onValueChange = onChange,
        label = "CREW NAME",
        placeholder = "Name your crew",
    )
}

@Composable
private fun ToggleStory() {
    var on by remember { mutableStateOf(true) }
    CatalogScene("Switch", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                FrGlassToggle(checked = false, onCheckedChange = {}, contentDescription = "Off")
                FrGlassToggle(checked = on, onCheckedChange = { on = it }, contentDescription = "On")
            }
        }
    }
}

@Composable
private fun RadioStory() {
    var selected by remember { mutableStateOf(0) }
    CatalogScene("Radio", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                FrGlassRadio(selected = selected == 0, onClick = { selected = 0 })
                FrGlassRadio(selected = selected == 1, onClick = { selected = 1 })
            }
        }
    }
}

@Composable
private fun RowStory() {
    CatalogScene("Divider-less rows", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column {
                listOf("Anika" to 9, "Reggie" to 8, "June" to 7).forEachIndexed { index, (name, score) ->
                    FrStructuralRow(
                        showTopHairline = index != 0,
                        leading = { FrGlassAvatar(name.take(2).uppercase(), size = 40.dp) },
                        trailing = { FrScoreDisc(score, size = 32.dp, tone = FrScoreTone.Olive) },
                    ) {
                        FrText(name, color = StructuralColors.foreground, style = StructuralType.titleMd)
                        FrText("LAST PLATE · 2H AGO", color = StructuralColors.foreground.copy(alpha = 0.6f), style = StructuralType.micro)
                    }
                }
            }
        }
    }
}

@Composable
private fun DockStory() {
    CatalogScene("Floating nav", lockedTheme = ThemeMode.Dark) {
        StructuralStage(modifier = Modifier.height(140.dp)) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                FrDock(
                    items = listOf(
                        FrDockItem(Icons.Filled.Home, "FEED"),
                        FrDockItem(Icons.Filled.Person, "CREW", badge = true),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                    onFabClick = {},
                    fabIcon = Icons.Filled.Add,
                    fabContentDescription = "Add a meal",
                    fabPulsing = true,
                )
            }
        }
    }
}

@Composable
private fun ContainersStory() {
    CatalogScene("Sheet + dialog", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CatalogSectionHeader("Dialog")
                FrGlassDialog {
                    FrText("Delete this meal?", style = StructuralType.titleMd, color = StructuralColors.foreground)
                    FrText("This cannot be undone.", style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.7f))
                    FrGlassButton("Delete meal", onClick = {}, tone = FrButtonTone.Danger, fillWidth = true)
                }
                CatalogSectionHeader("Sheet")
                FrGlassSheet {
                    FrText("Report", style = StructuralType.titleMd, color = StructuralColors.foreground)
                    FrText("Block member", style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun PassportStory() {
    CatalogScene("Badges + progress", lockedTheme = ThemeMode.Dark) {
        StructuralStage {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    FrBadgeDisc(earned = true, icon = Icons.Filled.Star, contentDescription = "First Plate — earned")
                    FrBadgeDisc(earned = false, icon = Icons.Filled.Lock, contentDescription = "Locked")
                    FrBadgeDisc(earned = true, icon = Icons.Filled.Home, contentDescription = "Host — earned")
                }
                Spacer(Modifier.height(Spacing.xs))
                FrBarTrack(0.6f, modifier = Modifier.fillMaxWidth())
                FrBarTrack(0.25f, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
