package es.schsebastian.foodrats.app.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.toFixed
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * Renders one [RecapScene] full-bleed. Each scene is a self-contained, domain-aware composable that
 * reuses design-system atoms (`FrBadge`, the `LocalFrSemanticColors` meaning roles) and resolves all
 * copy through StringKeys. Kept deliberately free of the player chrome (progress bar, gestures) so
 * Wave 3's shareable-cards can render the SAME scene composable off-screen to a bitmap.
 */
@Composable
fun RecapSceneView(scene: RecapScene, modifier: Modifier = Modifier) {
    when (scene) {
        is RecapScene.Cover -> CoverScene(scene, modifier)
        is RecapScene.TopMeal -> TopMealScene(scene, modifier)
        is RecapScene.BestCook -> CenteredScene(
            title = resolve(SharedStringKey.RecapBestCookTitle),
            subtitle = resolve(SharedStringKey.RecapBestCookSubtitle, scene.memberName, scene.avgScore.toFixed(1)),
            icon = FrIcons.Crown,
            tint = LocalFrSemanticColors.current.celebration,
            modifier = modifier,
        )
        is RecapScene.MostProlific -> CenteredScene(
            title = resolve(SharedStringKey.RecapMostProlificTitle),
            subtitle = resolve(SharedStringKey.RecapMostProlificSubtitle, scene.memberName, scene.postCount),
            icon = FrIcons.Restaurant,
            tint = LocalFrSemanticColors.current.celebration,
            modifier = modifier,
        )
        is RecapScene.Streak -> CenteredScene(
            title = resolve(SharedStringKey.RecapStreakTitle),
            subtitle = resolve(SharedStringKey.RecapStreakSubtitle, scene.streakDays),
            icon = FrIcons.Flame,
            tint = LocalFrSemanticColors.current.streakHot,
            modifier = modifier,
        )
        is RecapScene.Badges -> BadgesScene(scene, modifier)
        is RecapScene.Cuisines -> CenteredScene(
            title = resolve(SharedStringKey.RecapCuisinesTitle),
            subtitle = resolve(SharedStringKey.RecapCuisinesSubtitle, scene.collectedCount, scene.totalCount),
            icon = FrIcons.Public,
            tint = LocalFrSemanticColors.current.info,
            modifier = modifier,
        )
        is RecapScene.YourWeek -> YourWeekScene(scene, modifier)
    }
}

@Composable
private fun CoverScene(scene: RecapScene.Cover, modifier: Modifier) {
    SceneSurface(modifier, background = MaterialTheme.colorScheme.primary) {
        CenteredText(resolve(SharedStringKey.RecapCoverTitle), MaterialTheme.typography.headlineMedium, MaterialTheme.colorScheme.onPrimary)
        CenteredText(resolve(SharedStringKey.RecapCoverSubtitle), MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.onPrimary)
        CenteredText(scene.weekLabel, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun TopMealScene(scene: RecapScene.TopMeal, modifier: Modifier) {
    SceneSurface(modifier, background = MaterialTheme.colorScheme.scrim) {
        CenteredText(resolve(SharedStringKey.RecapTopMealTitle), MaterialTheme.typography.titleMedium, Color.White)
        AsyncImage(
            model = scene.photoUrl,
            contentDescription = scene.dishName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(220.dp),
        )
        CenteredText(scene.dishName, MaterialTheme.typography.headlineSmall, Color.White)
        CenteredText(resolve(SharedStringKey.RecapTopMealAuthor, scene.authorName), MaterialTheme.typography.bodyMedium, Color.White)
        CenteredText(
            resolve(SharedStringKey.RecapTopMealScore, scene.score.toFixed(1), scene.ratingCount),
            MaterialTheme.typography.titleSmall,
            LocalFrSemanticColors.current.celebration,
        )
    }
}

@Composable
private fun BadgesScene(scene: RecapScene.Badges, modifier: Modifier) {
    SceneSurface(modifier, background = MaterialTheme.colorScheme.scrim) {
        CenteredText(resolve(SharedStringKey.RecapBadgesTitle), MaterialTheme.typography.titleMedium, Color.White)
        scene.titleKeys.take(3).forEach { key: AchievementStringKey ->
            FrBadge(
                icon = FrIcons.Trophy,
                title = resolve(key),
                earned = true,
                progressFraction = 1f,
                tint = LocalFrSemanticColors.current.celebration,
            )
        }
    }
}

@Composable
private fun YourWeekScene(scene: RecapScene.YourWeek, modifier: Modifier) {
    SceneSurface(modifier, background = MaterialTheme.colorScheme.secondary) {
        CenteredText(resolve(SharedStringKey.RecapYourWeekTitle), MaterialTheme.typography.headlineMedium, MaterialTheme.colorScheme.onSecondary)
        CenteredText(resolve(SharedStringKey.RecapYourWeekStreak, scene.streakDays), MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSecondary)
        CenteredText(resolve(SharedStringKey.RecapYourWeekCuisines, scene.cuisinesCollected), MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSecondary)
        CenteredText(resolve(SharedStringKey.RecapYourWeekIngredients, scene.ingredientsCollected), MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSecondary)
    }
}

/** A generic icon + title + subtitle scene (best cook, most prolific, streak, cuisines). */
@Composable
private fun CenteredScene(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier,
) {
    SceneSurface(modifier, background = MaterialTheme.colorScheme.scrim) {
        FrBadge(icon = icon, title = title, earned = true, progressFraction = 1f, tint = tint)
        CenteredText(subtitle, MaterialTheme.typography.titleMedium, Color.White)
    }
}

@Composable
private fun CenteredText(text: String, style: TextStyle, color: Color) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SceneSurface(
    modifier: Modifier,
    background: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { content() }
    }
}
