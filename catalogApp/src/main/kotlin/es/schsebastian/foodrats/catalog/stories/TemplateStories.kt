package es.schsebastian.foodrats.catalog.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.LayoutDirection
import es.schsebastian.foodrats.catalog.components.CatalogScene
import es.schsebastian.foodrats.catalog.components.CatalogSceneSplit
import es.schsebastian.foodrats.catalog.registry.CatalogEntry
import es.schsebastian.foodrats.catalog.registry.CatalogGroup
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarWithName
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.templates.FrAwardShareCard
import es.schsebastian.foodrats.core.designsystem.templates.FrFeedLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrFormLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrPlateShareCard
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.templates.FrStreakShareCard
import es.schsebastian.foodrats.core.designsystem.templates.ShareCardFormat
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

internal fun templateStories(): List<CatalogEntry> = listOf(
    CatalogEntry("template.feedLayout",    CatalogGroup.TEMPLATES, "FrFeedLayout",    "Day header + list region") { FeedLayoutStory() },
    CatalogEntry("template.formLayout",    CatalogGroup.TEMPLATES, "FrFormLayout",    "Padded column for forms") { FormLayoutStory() },
    CatalogEntry("template.screenScaffold", CatalogGroup.TEMPLATES, "FrScreenScaffold", "App scaffold honoring safeDrawing insets") { ScreenScaffoldStory() },
    CatalogEntry("template.plateShareCard",  CatalogGroup.TEMPLATES, "FrPlateShareCard",  "Shareable plate card (1:1 + 9:16)") { PlateShareCardStory() },
    CatalogEntry("template.awardShareCard",  CatalogGroup.TEMPLATES, "FrAwardShareCard",  "Shareable award card (1:1 + 9:16)") { AwardShareCardStory() },
    CatalogEntry("template.streakShareCard", CatalogGroup.TEMPLATES, "FrStreakShareCard", "Shareable streak card (1:1 + 9:16)") { StreakShareCardStory() },
)

@Composable
private fun FeedLayoutStory() {
    CatalogScene(label = "Day header + list", padding = androidx.compose.foundation.layout.PaddingValues(Spacing.sm)) {
        FrFeedLayout(
            modifier = Modifier.fillMaxWidth(),
            dayHeader = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FrIconButton(icon = FrIcons.ChevronLeft, onClick = {}, contentDescription = "Prev")
                    Text("Tue · 2026-05-19", style = MaterialTheme.typography.titleMedium)
                    FrIconButton(icon = FrIcons.ChevronRight, onClick = {}, contentDescription = "Next")
                }
            },
            list = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf("Sebastián" to 8, "Anika" to 9, "Reggie" to 6).forEach { (name, score) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            FrAvatarWithName(initials = name.take(2), name = name)
                            FrScoreBadge(score = score)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun FormLayoutStory() {
    CatalogScene(label = "Form with two fields") {
        FrFormLayout(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.End,
            ) {
                FrLabeledTextField(
                    label = "Crew name",
                    value = "Rats of Tuesday",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                FrLabeledTextField(
                    label = "Invite code",
                    value = "RATS-42",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                FrButton(label = "Create crew", onClick = {})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffoldStory() {
    CatalogScene(label = "Top bar + bottom nav", padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(Radius.md)),
            color = MaterialTheme.colorScheme.surface,
        ) {
            FrScreenScaffold(
                topBar = {
                    CenterAlignedTopAppBar(title = { Text("FoodRats") })
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = { FrIcon(image = FrIcons.Home, contentDescription = "Feed") },
                            label = { Text("Feed") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = { FrIcon(image = FrIcons.Stats, contentDescription = "Stats") },
                            label = { Text("Stats") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = { FrIcon(image = FrIcons.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                        )
                    }
                },
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Screen body", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// Share cards take a decoded ImageBitmap (the platform renderer pre-decodes the signed URL — the
// card never loads from network). catalogApp depends only on :core:designsystem (no Coil/Koin), so
// the stories paint a static gradient placeholder instead of loading a URL — exactly why the cards
// take `plate: ImageBitmap?` rather than a URL.
@Composable
private fun samplePlate(): ImageBitmap {
    val w = 540
    val h = 540
    return remember {
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics.Canvas(bitmap),
            size = androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()),
        ) {
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFB0561E), Color(0xFF4F6E2B)),
                ),
            )
        }
        bitmap
    }
}

@Composable
private fun PlateShareCardStory() {
    val plate = samplePlate()
    CatalogSceneSplit(label = "Plate · 9:16 · with photo") {
        FrPlateShareCard(
            plate = plate,
            dishName = "Smoked brisket tacos",
            authorName = "Sebastián",
            scoreLabel = "8.4 ★ · 5",
            dayEmote = "🌮",
            footerBrand = "FoodRats",
            format = ShareCardFormat.Story,
            modifier = Modifier.width(180.dp),
        )
    }
    CatalogScene(label = "Square · long title · no photo / no score") {
        FrPlateShareCard(
            plate = null,
            dishName = "A really very extremely long dish name that has to wrap and ellipsize",
            authorName = "Anika",
            scoreLabel = null,
            dayEmote = "🍳",
            footerBrand = "FoodRats",
            format = ShareCardFormat.Square,
            modifier = Modifier.width(260.dp),
        )
    }
}

@Composable
private fun AwardShareCardStory() {
    val plate = samplePlate()
    CatalogSceneSplit(label = "Award · 9:16 · Best meal") {
        FrAwardShareCard(
            plate = plate,
            awardLabel = "Best meal",
            dishName = "Charred miso aubergine",
            authorName = "Reggie",
            scoreLabel = "9.1 ★ · 6",
            dayEmote = "🍆",
            footerBrand = "FoodRats",
            format = ShareCardFormat.Story,
            modifier = Modifier.width(180.dp),
        )
    }
    CatalogScene(label = "Square · Best cook · no photo") {
        FrAwardShareCard(
            plate = null,
            awardLabel = "Best cook",
            dishName = "Weeknight ragù",
            authorName = "Anika",
            scoreLabel = "8.0 ★ · 4",
            dayEmote = "🍝",
            footerBrand = "FoodRats",
            format = ShareCardFormat.Square,
            modifier = Modifier.width(260.dp),
        )
    }
}

@Composable
private fun StreakShareCardStory() {
    CatalogSceneSplit(label = "Streak · 9:16 · 14 days") {
        FrStreakShareCard(
            streakDays = 14,
            headline = "14-day streak 🔥",
            subline = "Keep it cooking",
            dayEmote = "🔥",
            footerBrand = "FoodRats",
            format = ShareCardFormat.Story,
            modifier = Modifier.width(180.dp),
        )
    }
    CatalogScene(label = "Square · 1 day (fresh streak)") {
        FrStreakShareCard(
            streakDays = 1,
            headline = "1-day streak 🔥",
            subline = "Keep it cooking",
            dayEmote = "🔥",
            footerBrand = "FoodRats",
            format = ShareCardFormat.Square,
            modifier = Modifier.width(260.dp),
        )
    }
}
