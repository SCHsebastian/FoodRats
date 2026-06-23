package es.schsebastian.foodrats.app.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import es.schsebastian.foodrats.app.i18n.SharedPluralKey
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrChipTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrMicroRow
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralChip
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.resolvePlural
import es.schsebastian.foodrats.core.i18n.toFixed
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * Renders one [RecapScene] full-bleed in the **Structural** language: an edge-to-edge [FrMediaFloor]
 * (the week's plate photo, or an appetizing dish "mood" brush) with bottom-anchored zero-chrome
 * content — an olive eyebrow, an oversized title / metric, and a microscopic stat array. Kept free of
 * the player chrome (progress bar, gestures, share CTA — all owned by [FrStoryScaffold]) so it stays a
 * pure slot. The share-card path rasterizes a *separate* composable ([RecapShareCardContent]), so the
 * floor's blur here never touches the rendered cards.
 */
@Composable
fun RecapSceneView(scene: RecapScene, modifier: Modifier = Modifier) {
    when (scene) {
        is RecapScene.Cover -> CoverScene(scene, modifier)
        is RecapScene.TopMeal -> TopMealScene(scene, modifier)
        is RecapScene.BestCook -> SceneFloor(modifier, brush = StructuralColors.dishMackerel) {
            FrEyebrow(text = resolve(SharedStringKey.RecapBestCookTitle).uppercase())
            Spacer(Modifier.height(Spacing.sm))
            MetricLine(value = scene.avgScore.toFixed(1), unit = resolve(SharedStringKey.RecapRatingUnit), tint = LocalFrSemanticColors.current.celebration)
            Spacer(Modifier.height(Spacing.sm))
            BodyLine(resolve(SharedStringKey.RecapBestCookSubtitle, scene.memberName, scene.avgScore.toFixed(1)))
        }
        is RecapScene.MostProlific -> SceneFloor(modifier, brush = StructuralColors.dishSalad) {
            FrEyebrow(text = resolve(SharedStringKey.RecapMostProlificTitle).uppercase())
            Spacer(Modifier.height(Spacing.sm))
            MetricLine(value = scene.postCount.toString(), unit = null, tint = LocalFrSemanticColors.current.celebration)
            Spacer(Modifier.height(Spacing.sm))
            BodyLine(resolve(SharedStringKey.RecapMostProlificSubtitle, scene.memberName, scene.postCount))
        }
        is RecapScene.Streak -> SceneFloor(modifier, brush = StructuralColors.dishRamen, blur = StructuralBlur.Heavy) {
            FrEyebrow(text = resolve(SharedStringKey.RecapStreakTitle).uppercase())
            Spacer(Modifier.height(Spacing.sm))
            MetricLine(value = scene.streakDays.toString(), unit = null, tint = LocalFrSemanticColors.current.streakHot)
            Spacer(Modifier.height(Spacing.sm))
            BodyLine(resolvePlural(SharedPluralKey.RecapStreakSubtitle, scene.streakDays))
        }
        is RecapScene.Badges -> BadgesScene(scene, modifier)
        is RecapScene.Cuisines -> SceneFloor(modifier, brush = StructuralColors.oliveFloor) {
            FrEyebrow(text = resolve(SharedStringKey.RecapCuisinesTitle).uppercase())
            Spacer(Modifier.height(Spacing.sm))
            MetricLine(
                value = scene.collectedCount.toString(),
                unit = resolve(SharedStringKey.RecapCuisinesRatio, scene.totalCount),
                tint = LocalFrSemanticColors.current.info,
            )
            Spacer(Modifier.height(Spacing.sm))
            BodyLine(resolve(SharedStringKey.RecapCuisinesSubtitle, scene.collectedCount, scene.totalCount))
        }
        is RecapScene.YourWeek -> YourWeekScene(scene, modifier)
    }
}

@Composable
private fun CoverScene(scene: RecapScene.Cover, modifier: Modifier) {
    SceneFloor(modifier, brush = StructuralColors.dishRamen, blur = StructuralBlur.Heavy) {
        FrEyebrow(
            text = resolve(SharedStringKey.RecapCoverTitle).uppercase(),
            color = StructuralColors.foreground.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(Spacing.sm))
        FrText(
            text = resolve(SharedStringKey.RecapCoverSubtitle),
            style = StructuralType.titleXl,
            color = StructuralColors.foreground,
        )
        Spacer(Modifier.height(Spacing.sm))
        FrText(
            text = scene.weekLabel.uppercase(),
            style = StructuralType.microMono,
            color = StructuralColors.foreground.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun TopMealScene(scene: RecapScene.TopMeal, modifier: Modifier) {
    // The plate IS the floor — sharp, the celebration of the week's best dish.
    val painter = rememberAsyncImagePainter(scene.photoUrl)
    SceneFloor(modifier, painter = painter, blur = StructuralBlur.None, scrim = FrScrimStyle.Photo) {
        FrEyebrow(text = resolve(SharedStringKey.RecapTopMealTitle).uppercase())
        Spacer(Modifier.height(Spacing.sm))
        FrText(text = scene.dishName, style = StructuralType.titleXl, color = StructuralColors.foreground)
        Spacer(Modifier.height(Spacing.sm))
        FrMicroRow(
            items = listOf(
                resolve(SharedStringKey.RecapTopMealAuthor, scene.authorName).uppercase(),
                resolve(SharedStringKey.RecapTopMealScore, scene.score.toFixed(1), scene.ratingCount).uppercase(),
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgesScene(scene: RecapScene.Badges, modifier: Modifier) {
    SceneFloor(modifier, brush = StructuralColors.dishMackerel) {
        FrEyebrow(text = resolve(SharedStringKey.RecapBadgesTitle).uppercase())
        Spacer(Modifier.height(Spacing.md))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            scene.titleKeys.take(3).forEach { key: AchievementStringKey ->
                FrStructuralChip(
                    label = resolve(key),
                    tone = FrChipTone.Ember,
                    leadingIcon = FrIcons.Trophy,
                )
            }
        }
    }
}

@Composable
private fun YourWeekScene(scene: RecapScene.YourWeek, modifier: Modifier) {
    SceneFloor(modifier, brush = StructuralColors.fieldFloor) {
        FrEyebrow(text = resolve(SharedStringKey.RecapYourWeekTitle).uppercase())
        Spacer(Modifier.height(Spacing.md))
        BodyLine(resolvePlural(SharedPluralKey.RecapYourWeekStreak, scene.streakDays))
        Spacer(Modifier.height(Spacing.xs))
        BodyLine(resolve(SharedStringKey.RecapYourWeekCuisines, scene.cuisinesCollected))
        Spacer(Modifier.height(Spacing.xs))
        BodyLine(resolve(SharedStringKey.RecapYourWeekIngredients, scene.ingredientsCollected))
    }
}

/** An oversized metric (weight 800, tabular) with an optional trailing unit, baseline-aligned. */
@Composable
private fun MetricLine(value: String, unit: String?, tint: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FrText(text = value, style = StructuralType.metricLg, color = tint)
        if (unit != null) {
            FrText(
                text = unit,
                style = StructuralType.titleLg,
                color = StructuralColors.foreground,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun BodyLine(text: String) {
    FrText(text = text, style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.85f))
}

/**
 * The shared structural scene surface: an edge-to-edge [FrMediaFloor] (Z0) with bottom-anchored
 * content inset under the player chrome (progress bar top, share CTA bottom).
 */
@Composable
private fun SceneFloor(
    modifier: Modifier,
    painter: Painter? = null,
    brush: Brush = StructuralColors.fieldFloor,
    blur: StructuralBlur = StructuralBlur.Heavy,
    scrim: FrScrimStyle = FrScrimStyle.Even,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        FrMediaFloor(painter = painter, brush = brush, blur = blur, dim = 0.42f, scrim = scrim)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.lg)
                .padding(top = 56.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            content()
        }
    }
}
